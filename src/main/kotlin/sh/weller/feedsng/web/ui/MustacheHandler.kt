package sh.weller.feedsng.web.ui

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Component
class MustacheHandler {

    fun getRouterFunction(): RouterFunction<ServerResponse> =
        coRouter {
            GET("/index.html", ::getIndex)
        }


    private suspend fun getIndex(request: ServerRequest): ServerResponse {

        return ServerResponse.ok().render("index").awaitSingle()
    }
}