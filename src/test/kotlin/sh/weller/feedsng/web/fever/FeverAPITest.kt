package sh.weller.feedsng.web.fever

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest
@AutoConfigureWebTestClient
class FeverAPITest(
    @Autowired private val client: WebTestClient
) {

    @Test
    fun contextLoads() {
        client
            .post()
            .uri("/api/fever.php?api")
            .exchange()
            .expectStatus()
            .is2xxSuccessful
    }

}