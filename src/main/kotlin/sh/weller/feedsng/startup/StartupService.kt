package sh.weller.feedsng.startup

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.api.provided.FeedControlService
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserQueryService

@Service
class StartupService(
    private val userQueryService: UserQueryService,
    private val userControlService: UserControlService,
    private val feedControlService: FeedControlService,
    @Value("classpath:data/StartupOPML.xml") private val startupOPML: Resource
) : SmartLifecycle {
    private var isStarted = false

    override fun start() {
        logger.info("Starting Startup Service")
        runBlocking {
            val userName = "FeedsNG"
            if (userQueryService.getUserByUsername(userName) == null) {
                val userId = userControlService.createUser(userName, "Some.Random.Password!")
                val feverAPIKey = userControlService.enableFeverAPI(userId)
                logger.info("Created User $userName - Fever API Key: $feverAPIKey")

                val startupOPMLContent = startupOPML.inputStream.reader().readText()
                when (val importResult = feedControlService.importFromOPML(userId, startupOPMLContent)) {
                    is Failure -> logger.info("Error during import: ${importResult.reason}")
                    is Success -> logger.info("Successfully imported all feeds")
                }
            } else {
                logger.info("User $userName already exists - skipping setup.")
            }
        }
        isStarted = true
    }

    override fun stop() {
        this.isStarted = false
    }

    override fun isRunning(): Boolean = isStarted

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StartupService::class.java)
    }
}