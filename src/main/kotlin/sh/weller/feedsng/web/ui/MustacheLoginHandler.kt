package sh.weller.feedsng.web.ui

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.web.support.WebRequestHandler

@Controller
class MustacheLoginHandler : WebRequestHandler {
    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/login", permitAll)
    }

    override fun getRouterFunction(): RouterFunction<ServerResponse> = coRouter {
        GET("/login", ::getLoginPage)
    }

    private suspend fun getLoginPage(request: ServerRequest): ServerResponse {
        val csrfToken: CsrfToken? = request.attributeOrNull("_csrf") as CsrfToken?

        val model = if (csrfToken != null) {
            mapOf(csrfToken.parameterName to csrfToken.token)
        } else {
            emptyMap()
        }
        return ServerResponse.ok()
            .render("sites/login", model)
            .awaitSingle()
    }
}

