package sh.weller.feedsng.database

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.get
import java.util.*

class FlywayContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment

        val r2dbcURL = environment["spring.r2dbc.url"]
        val r2dbcUsername = environment["spring.r2dbc.username"]
        val r2dbcPassword = environment["spring.r2dbc.password"]

        val flywayURL = r2dbcURL?.let {
            val jdbcURL = it.replaceFirst("r2dbc", "jdbc")
            if (jdbcURL.contains("h2:mem")) {
                return@let ("$jdbcURL;DB_CLOSE_DELAY=-1").replace("mem:///~", "mem:~")
            }
            return@let jdbcURL
        }

        val properties = Properties()
        properties["spring.flyway.locations"] = "classpath:db/migration/{vendor}"
        properties.setIfNotNull("spring.flyway.url", flywayURL)
        properties.setIfNotNull("spring.flyway.user", r2dbcUsername)
        properties.setIfNotNull("spring.flyway.password", r2dbcPassword)

        environment.propertySources.addFirst(PropertiesPropertySource("customFlywayProperties", properties))
    }

    private fun Properties.setIfNotNull(key: String, value: String?) {
        if (value != null) {
            this[key] = value
        }
    }
}