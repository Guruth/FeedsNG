package sh.weller.feedsng.web.fever

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.FeedControlService
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserQueryService
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*
import kotlin.test.Test

@SpringBootTest
@AutoConfigureWebTestClient
class FeverAPIHandlerTest(
    @Autowired private val feedControlService: FeedControlService,
    @Autowired private val userControlService: UserControlService,
    @Autowired private val userQueryService: UserQueryService,
    @Autowired private val webTestClient: WebTestClient
) {

    private suspend fun getUserAndApiKey(): Pair<User, String> {
        val userId = userControlService.createUser(UUID.randomUUID().toString(), "TestPassword")
        val user = userQueryService.getUserByUserId(userId)!!

        userControlService.enableFeverAPI(userId)
        val feverApiKey = userQueryService.getUserByUserId(userId)!!.userData.feverAPIKeyHash!!

        return user to feverApiKey
    }

    @Test
    fun `Valid authorization required`(): Unit = runBlocking {
        val (user, feverApiKey) = getUserAndApiKey()
        webTestClient
            .post()
            .uri(FeverAPIHandler.feverAPIPath)
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient
            .post()
            .uri(FeverAPIHandler.feverAPIPath)
            .body(BodyInserters.fromFormData("api_key", "asdf"))
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient
            .post()
            .uri(FeverAPIHandler.feverAPIPath)
            .body(BodyInserters.fromFormData("api_key", feverApiKey))
            .exchange()
            .expectStatus().is2xxSuccessful
    }

    @Test
    fun `Groups, Feeds, FeedItems`(): Unit =
        runBlocking {
            val (user, feverApiKey) = getUserAndApiKey()
            val groupId = feedControlService.addGroup(user.userId, "TestGroup")

            val feedIdInGroup = feedControlService
                .addFeedToGroup(user.userId, groupId, "https://blog.jetbrains.com/kotlin/feed/")
                .onFailure { throw IllegalArgumentException("Feed not added") }

            val feedId = feedControlService
                .addFeed(user.userId, "https://blog.jetbrains.com/feed/")
                .onFailure { throw IllegalArgumentException("Feed not added") }

            val feverResponse = webTestClient
                .post()
                .uri("${FeverAPIHandler.feverAPIPath}?groups&feeds&items")
                .body(BodyInserters.fromFormData("api_key", feverApiKey))
                .exchange()
                .expectStatus().is2xxSuccessful
                .expectBody<FeverResponse>().returnResult().responseBody

            expectThat(feverResponse)
                .isNotNull()
                .and {
                    get { lastRefreshedOnTime }
                        .isNotNull()

                    get { feedsGroups }
                        .isNotNull()
                        .isNotEmpty()

                    get { groups }
                        .isNotNull()
                        .isNotEmpty()
                        .map { it.id }
                        .contains(groupId.id)

                    get { feeds }
                        .isNotNull()
                        .isNotEmpty()
                        .map { it.id }
                        .contains(feedId.id, feedIdInGroup.id)

                    get { totalItems }
                        .isNotNull()
                        .isGreaterThan(1)
                }

        }
}