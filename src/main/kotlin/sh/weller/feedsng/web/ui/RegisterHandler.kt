package sh.weller.feedsng.web.ui

import kotlinx.serialization.Serializable
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.user.api.provided.CreateUserResult
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.web.support.WebRequestHandler
import java.net.URI

@Controller
class RegisterHandler(
    val userControlService: UserControlService
) : WebRequestHandler {
    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/register", permitAll)
    }

    override fun getCSRFPathPatternMatcher(): PathPatternParserServerWebExchangeMatcher? =
        PathPatternParserServerWebExchangeMatcher("/register", HttpMethod.POST)

    override fun getRouterFunction(): RouterFunction<ServerResponse> = coRouter {
        GET("/register", ::getRegisterPage)
        POST("/register", ::registerAccount)
    }

    private suspend fun getRegisterPage(request: ServerRequest): ServerResponse {
        val modelMap = request.getModelMap()

        return ServerResponse.ok().renderAndAwait("sites/register", modelMap)
    }

    suspend fun registerAccount(request: ServerRequest): ServerResponse {
        val registerModel = request.awaitBody<RegisterModel>()

        val createUserResult = userControlService
            .createUser(registerModel.username, registerModel.password, registerModel.inviteCode)

        return when (createUserResult) {
            CreateUserResult.InviteCodeInvalid -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                .bodyValueAndAwait("Invite code is invalid!")
            CreateUserResult.PasswordNotValid -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                .bodyValueAndAwait("Password not valid! Must be at least 8 characters long.")
            CreateUserResult.UsernameAlreadyExist -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                .bodyValueAndAwait("Username already in use!")
            is CreateUserResult.Success -> ServerResponse.created(URI("/login")).buildAndAwait()
        }
    }
}

@Serializable
private data class RegisterModel(
    val username: String,
    val password: String,
    val inviteCode: String
)