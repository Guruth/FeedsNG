package sh.weller.feedsng.web

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import sh.weller.feedsng.web.fever.FeverAPIHandler

@Configuration
class WebConfiguration : WebFluxConfigurer {

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
