package sh.weller.feedsng.database.h2r2dbc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitOneOrNull
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.provided.toUserId
import sh.weller.feedsng.user.api.required.UserRepository

@Suppress("SqlResolve")
class H2R2DBCUserRepository(
    private val client: DatabaseClient
) : UserRepository {

    init {
        runBlocking {
            client.sql(
                """CREATE TABLE IF NOT EXISTS user ( 
                    |id INTEGER AUTO_INCREMENT PRIMARY KEY, 
                    |name VARCHAR(256), 
                    |password_hash VARCHAR(256),
                    |fever_api_key_hash VARCHAR(2048),
                    |)""".trimMargin()
            ).await()
        }
    }

    override suspend fun getByUsername(username: String): User? =
        withContext(Dispatchers.IO) {
            client.sql("SELECT id, name, password_hash, fever_api_key_hash FROM user WHERE username = :username")
                .bind("username", username)
                .mapToUser()
                .awaitOneOrNull()
        }


    override suspend fun getByFeverAPIKey(feverAPIKeyHash: String): User? =
        withContext(Dispatchers.IO) {
            client.sql("SELECT id, name, password_hash, fever_api_key_hash FROM user WHERE fever_api_key_hash = :feverAPIKeyHash")
                .bind("feverAPIKeyHash", feverAPIKeyHash)
                .mapToUser()
                .awaitOneOrNull()
        }

    override suspend fun insertUser(userData: UserData): UserId =
        withContext(Dispatchers.IO) {
            client.sql("INSERT INTO user(username, password_hash) VALUES (:username, :passwordHash)")
                .bind("username", userData.username)
                .bind("passwordHash", userData.passwordHash)
                .filter { s -> s.returnGeneratedValues() }
                .map { row -> row.get("id", Integer::class.java)!!.toInt() }
                .awaitOne()
                .toUserId()
        }
}