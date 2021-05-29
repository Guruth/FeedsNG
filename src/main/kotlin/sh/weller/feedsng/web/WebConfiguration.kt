package sh.weller.feedsng.web

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import sh.weller.feedsng.web.support.WebRequestHandler
import java.net.URI


@Configuration
@EnableWebFluxSecurity
class WebConfiguration : WebFluxConfigurer {

    @Bean
    fun springWebFilterChain(
        http: ServerHttpSecurity,
        handlers: List<WebRequestHandler>
    ): SecurityWebFilterChain {
        return http {
            authorizeExchange {
                for (handler in handlers) {
                    handler.apply { addAuthorization() }
                }
            }
            cors { disable() }
            csrf {
            }
            httpBasic { disable() }
            formLogin {
                loginPage = "/login"
                authenticationSuccessHandler = RedirectServerAuthenticationSuccessHandler("/reader")
            }
            logout {
                logoutUrl = "/logout"
                logoutSuccessHandler = logoutSuccessHandler("/")
            }
        }
    }

    fun logoutSuccessHandler(uri: String): ServerLogoutSuccessHandler {
        val successHandler = RedirectServerLogoutSuccessHandler()
        successHandler.setLogoutSuccessUrl(URI.create(uri))
        return successHandler
    }

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer
            .defaultCodecs()
            .kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(Json {
                encodeDefaults = false
            }))
    }

    @Bean
    fun feverAPIRouter(handlers: List<WebRequestHandler>): RouterFunction<*> =
        handlers
            .map { it.getRouterFunction() }
            .reduce { acc, routerFunction -> acc.and(routerFunction) }

}

