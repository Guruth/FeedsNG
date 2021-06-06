package sh.weller.feedsng.web.ui

import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.web.support.WebRequestHandler

@Controller
class WelcomePageHandler : WebRequestHandler {
    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/", permitAll)
        authorize("/favicon.ico", permitAll)
        authorize("/webjars/**", permitAll)
    }

    override fun getCSRFPathPatternMatcher(): PathPatternParserServerWebExchangeMatcher? = null

    override fun getRouterFunction(): RouterFunction<ServerResponse> = coRouter {
        GET("/", ::getWelcomePage)
    }

    private suspend fun getWelcomePage(request: ServerRequest): ServerResponse {
        val modelMap = request.getModelMap()
        return ServerResponse.ok()
            .renderAndAwait("sites/welcome", modelMap)
    }
}