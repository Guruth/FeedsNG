package sh.weller.feedsng.web.support

import kotlinx.serialization.Serializable
import org.springframework.core.ResolvableType
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
final class JsonAuthenticationWebFilter(
    reactiveAuthenticationManager: ReactiveAuthenticationManager,
    serverSecurityContextRepository: ServerSecurityContextRepository,
    private val serverCodecConfigurer: ServerCodecConfigurer
) : AuthenticationWebFilter(reactiveAuthenticationManager) {

    private val usernamePassword = ResolvableType.forClass(UsernamePassword::class.java)

    init {
        // Spring Security Repository
        setSecurityContextRepository(serverSecurityContextRepository)

        // URL where to reach this handler
        setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/login"))

        // Successful auth handler
        setAuthenticationSuccessHandler { webFilterExchange, _ ->
            webFilterExchange.exchange.response.statusCode = HttpStatus.OK
            return@setAuthenticationSuccessHandler Mono.empty()
        }
        // Failed auth handler
        setAuthenticationFailureHandler { webFilterExchange, _ ->
            webFilterExchange.exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return@setAuthenticationFailureHandler Mono.empty()
        }

        // Unwrap the username & password
        setServerAuthenticationConverter(this::authenticate)
    }

    private fun authenticate(exchange: ServerWebExchange): Mono<Authentication> {
        val request: ServerHttpRequest = exchange.request
        val contentType: MediaType = request.headers.contentType
            ?: return Mono.empty()

        if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            val reader = serverCodecConfigurer.readers
                .firstOrNull { it.canRead(usernamePassword, MediaType.APPLICATION_JSON) }
                ?: return Mono.error(IllegalStateException("No JSON reader for UsernamePassword"))
            return reader.readMono(this.usernamePassword, request, emptyMap())
                .cast(UsernamePassword::class.java)
                .map { UsernamePasswordAuthenticationToken(it.username, it.password) }
        } else {
            return Mono.empty()
        }
    }
}

@Serializable
data class UsernamePassword(
    val username: String,
    val password: String
)