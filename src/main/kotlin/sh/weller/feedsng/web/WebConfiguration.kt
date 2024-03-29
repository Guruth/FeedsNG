package sh.weller.feedsng.web

import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
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
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.session.ReactiveMapSessionRepository
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import sh.weller.feedsng.web.support.JsonAuthenticationWebFilter
import sh.weller.feedsng.web.support.WebRequestHandler
import java.net.URI
import java.time.Duration


@Configuration
@EnableWebFluxSecurity
@EnableSpringWebSession
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
                requireCsrfProtectionMatcher =
                    AndServerWebExchangeMatcher(handlers.mapNotNull { it.getCSRFPathPatternMatcher() })
            }
            logout {
                logoutUrl = "/logout"
                logoutSuccessHandler = RedirectServerLogoutSuccessHandler()
                    .apply { setLogoutSuccessUrl(URI("/")) }
                // TODO: delete session cookie
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

    @Bean
    fun reactiveMapSessionRepository(
        @Value("\${spring.session.timeout:30m}") sessionTimeout: Duration
    ) = ReactiveMapSessionRepository(mutableMapOf())
        .apply {
            this.setDefaultMaxInactiveInterval(sessionTimeout.toSeconds().toInt())
        }

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        val kotlinJson = Json {
            encodeDefaults = false
        }
        configurer
            .defaultCodecs()
            .kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(kotlinJson))
        configurer
            .defaultCodecs()
            .kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(kotlinJson))
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
    fun routerFunction(handlers: List<WebRequestHandler>): RouterFunction<*> {
        val routerFunctions = handlers
            .map { it.getRouterFunction() }
            .reduce { acc, routerFunction -> acc.and(routerFunction) }

        return coRouter {
            onError<Exception> { throwable, serverRequest ->
                defaultErrorHandlerLogger.error("Uncaught Error for url: ${serverRequest.uri()} ", throwable)
                return@onError ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).buildAndAwait()
            }
            add(routerFunctions)
        }
    }

    companion object {
        private val defaultErrorHandlerLogger: Logger = LoggerFactory.getLogger("DefaultWebErrorHandler")
    }
}
