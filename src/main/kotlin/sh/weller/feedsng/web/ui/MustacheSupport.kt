package sh.weller.feedsng.web.ui

import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitPrincipal


suspend fun ServerRequest.getUsername(): String? = this.awaitPrincipal()?.name


