package sh.weller.feedsng.web.support;

import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

interface WebRequestHandler {
    fun AuthorizeExchangeDsl.addAuthorization()
    fun getRouterFunction(): RouterFunction<ServerResponse>;
}
