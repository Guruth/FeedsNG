package sh.weller.feedsng.database.h2

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata


class H2Condition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val r2dbcURL = context.environment.getProperty("spring.r2dbc.url")
        return r2dbcURL == null || r2dbcURL.startsWith("r2dbc:h2:")
    }
}