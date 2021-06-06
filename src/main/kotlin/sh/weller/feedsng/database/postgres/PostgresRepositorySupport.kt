package sh.weller.feedsng.database.postgres

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class PostgresCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getProperty("spring.r2dbc.url")?.startsWith("r2dbc:postgresql:") ?: false
}
