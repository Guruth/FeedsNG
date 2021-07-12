package sh.weller.feedsng.database.postgres

import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Conditional
import org.springframework.r2dbc.core.*
import org.springframework.stereotype.Repository
import sh.weller.feedsng.database.getReified
import sh.weller.feedsng.database.mapToUser
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.provided.toUserId
import sh.weller.feedsng.user.api.required.UserRepository

@Conditional(PostgresCondition::class)
@Repository
class PostgresUserRepository(
    private val client: DatabaseClient
) : UserRepository {

    init {
        runBlocking {
            client.sql(
                """CREATE TABLE IF NOT EXISTS account ( 
                    |id SERIAL PRIMARY KEY, 
                    |username VARCHAR(256) UNIQUE, 
                    |password_hash VARCHAR(256),
                    |fever_api_key_hash VARCHAR(2048)
                    |)""".trimMargin()
            ).await()

            client.sql(
                """CREATE TABLE IF NOT EXISTS invite_code ( 
                    |issued_by INTEGER, 
                    |invite_code VARCHAR(256) UNIQUE, 
                    |used_by INTEGER
                    |)""".trimMargin()
            ).await()
        }
    }

    override suspend fun getByUserId(userId: UserId): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM account WHERE id = :id")
            .bind("id", userId.id)
            .mapToUser()
            .awaitOneOrNull()

    override suspend fun getByUsername(username: String): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM account WHERE username = :username")
            .bind("username", username)
            .mapToUser()
            .awaitOneOrNull()


    override suspend fun getFeverAPIAuthentication(feverAPIKeyHash: String): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM account WHERE fever_api_key_hash = :feverAPIKeyHash")
            .bind("feverAPIKeyHash", feverAPIKeyHash)
            .mapToUser()
            .awaitOneOrNull()


    override suspend fun insertUser(userData: UserData): UserId =
        client.sql("INSERT INTO account(username, password_hash) VALUES (:username, :passwordHash)")
            .bind("username", userData.username)
            .bind("passwordHash", userData.passwordHash)
            .filter { s -> s.returnGeneratedValues() }
            .map { row -> row.get("id", Integer::class.java)!!.toInt() }
            .awaitOne()
            .toUserId()


    override suspend fun setFeverAPIAuthentication(userId: UserId, feverAPIKeyHash: String) =
        client.sql("UPDATE account SET fever_api_key_hash = :feverApiKeyHash WHERE id = :id")
            .bind("id", userId.id)
            .bind("feverApiKeyHash", feverAPIKeyHash)
            .await()


    override suspend fun insertInviteCode(issuerUserId: UserId, inviteCode: String) {
        client.sql("INSERT INTO invite_code(issued_by, invite_code) VALUES (:issued_by, :invite_code)")
            .bind("issued_by", issuerUserId.id)
            .bind("invite_code", inviteCode)
            .await()
    }

    override suspend fun isInviteCodeUsed(inviteCode: String): Boolean {
        return client.sql("SELECT (CASE WHEN used_by IS NULL THEN false ELSE true END) as is_used FROM invite_code WHERE invite_code = :invite_code")
            .bind("invite_code", inviteCode)
            .map { row -> row.getReified<Boolean>("is_used") }
            .awaitSingleOrNull()
            ?: false
    }

    override suspend fun setInviteCodeUsed(usedByUserId: UserId, inviteCode: String) {
        client.sql("UPDATE invite_code SET used_by = :user_id WHERE invite_code = :invite_code")
            .bind("user_id", usedByUserId.id)
            .bind("invite_code", inviteCode)
            .await()
    }

}