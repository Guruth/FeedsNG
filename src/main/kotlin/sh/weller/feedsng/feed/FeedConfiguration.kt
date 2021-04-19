package sh.weller.feedsng.feed

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sh.weller.feedsng.feed.impl.FeedUpdateConfiguration
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Configuration
class FeedConfiguration {

    @Bean
    fun feedUpdateConfiguration() = FeedUpdateConfiguration(
        initialDelay = Duration.minutes(5).inWholeMilliseconds,
        updateInterval = Duration.minutes(10).inWholeMilliseconds
    )
}