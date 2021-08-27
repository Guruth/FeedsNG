package sh.weller.feedsng.web.fever

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.FeedControlService
import sh.weller.feedsng.feed.api.provided.toFeedItemId
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.CreateUserResult
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserQueryService
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb"
    ]
)
@AutoConfigureWebTestClient
class FeverAPIHandlerTest(
    @Autowired private val feedRepository: FeedRepository,
    @Autowired private val feedControlService: FeedControlService,
    @Autowired private val userControlService: UserControlService,
    @Autowired private val userQueryService: UserQueryService,
    @LocalServerPort private val serverPort: Int
) {

    private val webTestClient = WebTestClient
        .bindToServer()
        .baseUrl("http://localhost:$serverPort")
        .codecs {
            it.defaultCodecs().maxInMemorySize(-1)
        }
        .build()

    private suspend fun getUserAndApiKey(): Pair<User, String> {
        val createUserResult = userControlService.createUser(UUID.randomUUID().toString(), "TestPassword")
        assertIs<CreateUserResult.Success>(createUserResult)
        val user = userQueryService.getUserByUserId(createUserResult.userId)!!

        userControlService.enableFeverAPI(createUserResult.userId)
        val feverApiKey = userQueryService.getUserByUserId(createUserResult.userId)!!.userData.feverAPIKeyHash!!

        return user to feverApiKey
    }

    @Test
    fun `Valid authorization required`(): Unit = runBlocking {
        val (_, feverApiKey) = getUserAndApiKey()
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


    @Test
    fun `save and read feed items`(): Unit =
        runBlocking {
            val (user, feverApiKey) = getUserAndApiKey()

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

                    get { feeds }
                        .isNotNull()
                        .isNotEmpty()
                        .map { it.id }
                        .contains(feedId.id)

                    get { totalItems }
                        .isNotNull()
                        .isGreaterThan(1)
                }

            // Check if save works
            val firstItem = feverResponse?.items?.first()
            assertNotNull(firstItem)

            webTestClient
                .post()
                .uri("${FeverAPIHandler.feverAPIPath}?mark=item&id=${firstItem.id}&as=saved")
                .body(BodyInserters.fromFormData("api_key", feverApiKey))
                .exchange()
                .expectStatus().is2xxSuccessful

            val savedItem = feedRepository.getFeedItemOfUser(user.userId, feedId, firstItem.id.toFeedItemId())
            expectThat(savedItem)
                .isNotNull()
                .and {
                    get { isSaved }.isTrue()
                }

            // Check if read works
            val secondItem = feverResponse.items?.drop(1)?.first()
            assertNotNull(secondItem)

            webTestClient
                .post()
                .uri("${FeverAPIHandler.feverAPIPath}?mark=item&id=${secondItem.id}&as=read")
                .body(BodyInserters.fromFormData("api_key", feverApiKey))
                .exchange()
                .expectStatus().is2xxSuccessful

            val readItem = feedRepository.getFeedItemOfUser(user.userId, feedId, secondItem.id.toFeedItemId())
            expectThat(readItem)
                .isNotNull()
                .and {
                    get { isRead }.isTrue()
                }

        }
}