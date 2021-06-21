package sh.weller.feedsng.web.ui

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserQueryService
import sh.weller.feedsng.web.support.WebRequestHandler

@Controller
class AccountHandlerHandler(
    val userControlService: UserControlService,
    val userQueryService: UserQueryService
) : WebRequestHandler {
    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/account/**", authenticated)
    }

    override fun getCSRFPathPatternMatcher(): ServerWebExchangeMatcher? =
        PathPatternParserServerWebExchangeMatcher("/account/**", HttpMethod.POST)

    override fun getRouterFunction(): RouterFunction<ServerResponse> = coRouter {
        GET("/account", ::getAccountPage)
        POST("/account/invite", ::generateInviteCode)
    }

    private suspend fun getAccountPage(request: ServerRequest): ServerResponse {
        val modelMap = request.getModelMap()

        return ServerResponse.ok().renderAndAwait("sites/account", modelMap)
    }

    private suspend fun generateInviteCode(request: ServerRequest): ServerResponse {
        val username = request.getUsernameOrNull()
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val user = userQueryService.getUserByUsername(username)
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val inviteCode = userControlService.createInviteCode(user.userId)
            ?: return ServerResponse.status(HttpStatus.BAD_REQUEST).buildAndAwait()

        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(inviteCode)
    }
}