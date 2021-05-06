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
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import sh.weller.feedsng.web.fever.FeverAPIHandler

@Configuration
@EnableWebFluxSecurity
class WebConfiguration : WebFluxConfigurer {

    @Bean
    fun springWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http {
            authorizeExchange {
                authorize("/api/fever.php", permitAll)
            }
            cors { disable() }
            csrf { disable() }
            httpBasic { disable() }
            formLogin { }
        }
    }

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer
            .defaultCodecs()
            .kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(Json {
                encodeDefaults = false
            }))
    }

    @Bean
    fun feverAPIRouter(feverAPI: FeverAPIHandler): RouterFunction<*> = feverAPI.getRouterFunction()
}
