package sh.weller.feedsng.web.ui

import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.web.support.WebRequestHandler

@Controller
class LoginPageHandler : WebRequestHandler {
    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/login", permitAll)
    }

    override fun getCSRFPathPatternMatcher(): ServerWebExchangeMatcher? =
        PathPatternParserServerWebExchangeMatcher("/login", HttpMethod.POST)

    override fun getRouterFunction(): RouterFunction<ServerResponse> = coRouter {
        GET("/login", ::getLoginPage)
    }

    private suspend fun getLoginPage(request: ServerRequest): ServerResponse {
        val modelMap = request.getModelMap()
        return ServerResponse.ok()
            .renderAndAwait("sites/login", modelMap)
    }

}
