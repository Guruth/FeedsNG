package sh.weller.feedsng.feed

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sh.weller.feedsng.feed.impl.FeedUpdateConfiguration
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@ExperimentalTime
@Configuration
class FeedConfiguration {

    @Bean
    fun feedUpdateConfiguration() = FeedUpdateConfiguration(
        initialDelay = 5.minutes.toLongMilliseconds(),
        updateInterval = 10.minutes.toLongMilliseconds()
    )
}