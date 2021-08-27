package sh.weller.feedsng.database.h2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.database.AbstractFeedRepositoryTest

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb"
    ]
)
internal class H2FeedRepositoryTest(
    @Autowired databaseClient: DatabaseClient,
    @Autowired feedRepository: H2FeedRepository
) : AbstractFeedRepositoryTest(databaseClient, feedRepository)