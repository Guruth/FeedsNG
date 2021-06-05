package sh.weller.feedsng.web.ui

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.server.ServerRequest

suspend fun ServerRequest.getModelMap(): MutableMap<String, Any> {
    val modelMap = mutableMapOf<String, Any>()
    addUsernameIfPresent(modelMap)
    return modelMap
}

private suspend fun ServerRequest.addUsernameIfPresent(modelMap: MutableMap<String, Any>) {
    val principal = principal().awaitSingleOrNull()
        ?: return
    modelMap["username"] = principal.name
}