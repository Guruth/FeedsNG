package sh.weller.feedsng.web

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import sh.weller.feedsng.web.support.WebRequestHandler
import sh.weller.feedsng.web.ui.JsonAuthenticationWebFilter
import java.net.URI
import java.time.Duration


@Configuration
@EnableWebFluxSecurity
class WebConfiguration : WebFluxConfigurer {

    @Bean
    fun springWebFilterChain(
        http: ServerHttpSecurity,
        handlers: List<WebRequestHandler>,
        jsonAuthenticationWebFilter: JsonAuthenticationWebFilter
    ): SecurityWebFilterChain {
        return http {
            authorizeExchange {
                for (handler in handlers) {
                    handler.apply { addAuthorization() }
                }
            }
            cors { disable() }
            csrf {
                csrfTokenRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse()
            }
            logout {
                logoutUrl = "/logout"
                logoutSuccessHandler = RedirectServerLogoutSuccessHandler()
                    .apply { setLogoutSuccessUrl(URI("/")) }
            }

            // Disable others Authentication
            httpBasic { disable() }
            formLogin { disable() }
            exceptionHandling {
                // Redirect to the login page, if access is denied
                this.authenticationEntryPoint = RedirectServerAuthenticationEntryPoint("/login")
            }
            // Custom login handler that accepts json payloads
            addFilterAt(jsonAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        }
    }

    @Bean
    fun addCsrfToken(): WebFilter? {
        return WebFilter { exchange: ServerWebExchange, next: WebFilterChain ->
            return@WebFilter exchange
                .getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name)
                ?.doOnSuccess { _ -> }
                ?.then(next.filter(exchange))
                ?: return@WebFilter next.filter(exchange)
        }
    }

    @Bean
    fun reactiveAuthenticationManager(
        reactiveUserDetailsService: ReactiveUserDetailsService
    ) = UserDetailsRepositoryReactiveAuthenticationManager(reactiveUserDetailsService)


    @Bean
    fun serverSecurityContextRepository() = WebSessionServerSecurityContextRepository()

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer
            .defaultCodecs()
            .kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(Json {
                encodeDefaults = false
            }))
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        super.addResourceHandlers(registry)
        registry.addResourceHandler("/webjars/**")
            .addResourceLocations("classpath:/META-INF/resources/webjars/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))

        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
    }

    @Bean
    fun feverAPIRouter(handlers: List<WebRequestHandler>): RouterFunction<*> =
        handlers
            .map { it.getRouterFunction() }
            .reduce { acc, routerFunction -> acc.and(routerFunction) }

}
