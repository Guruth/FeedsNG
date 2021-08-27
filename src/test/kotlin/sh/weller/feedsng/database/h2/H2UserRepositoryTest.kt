package sh.weller.feedsng.database.h2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.database.AbstractUserRepositoryTest

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb"
    ]
)
internal class H2UserRepositoryTest(
    @Autowired databaseClient: DatabaseClient,
    @Autowired private val userRepository: H2UserRepository
) : AbstractUserRepositoryTest(databaseClient, userRepository)