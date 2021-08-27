package sh.weller.feedsng.database.h2

import org.springframework.r2dbc.core.*
import org.springframework.stereotype.Repository
import sh.weller.feedsng.database.getReified
import sh.weller.feedsng.database.mapToUser
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.provided.toUserId
import sh.weller.feedsng.user.api.required.UserRepository

@H2RepositoryCondition
@Repository
class H2UserRepository(
    private val client: DatabaseClient
) : UserRepository {

    override suspend fun getByUserId(userId: UserId): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM FEEDSNG.account WHERE id = :id")
            .bind("id", userId.id)
            .mapToUser()
            .awaitOneOrNull()

    override suspend fun getByUsername(username: String): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM FEEDSNG.account WHERE username = :username")
            .bind("username", username)
            .mapToUser()
            .awaitOneOrNull()


    override suspend fun getFeverAPIAuthentication(feverAPIKeyHash: String): User? =
        client.sql("SELECT id, username, password_hash, fever_api_key_hash FROM FEEDSNG.account WHERE fever_api_key_hash = :feverAPIKeyHash")
            .bind("feverAPIKeyHash", feverAPIKeyHash)
            .mapToUser()
            .awaitOneOrNull()


    override suspend fun insertUser(userData: UserData): UserId =
        client.sql("INSERT INTO FEEDSNG.account(username, password_hash) VALUES (:username, :passwordHash)")
            .bind("username", userData.username)
            .bind("passwordHash", userData.passwordHash)
            .filter { s -> s.returnGeneratedValues() }
            .map { row -> row.get("id", Integer::class.java)!!.toInt() }
            .awaitOne()
            .toUserId()


    override suspend fun setFeverAPIAuthentication(userId: UserId, feverAPIKeyHash: String) =
        client.sql("UPDATE FEEDSNG.account SET fever_api_key_hash = :feverApiKeyHash WHERE id = :id")
            .bind("id", userId.id)
            .bind("feverApiKeyHash", feverAPIKeyHash)
            .await()

    override suspend fun insertInviteCode(issuerUserId: UserId, inviteCode: String) {
        client.sql("INSERT INTO FEEDSNG.invite_code(issued_by, invite_code) VALUES (:issued_by, :invite_code)")
            .bind("issued_by", issuerUserId.id)
            .bind("invite_code", inviteCode)
            .await()
    }

    override suspend fun isInviteCodeUsed(inviteCode: String): Boolean {
        return client
            .sql("SELECT CASE WHEN used_by IS NULL THEN false ELSE true END as is_used FROM FEEDSNG.invite_code WHERE invite_code = :invite_code")
            .bind("invite_code", inviteCode)
            .map { row -> row.getReified<Boolean>("is_used") }
            .awaitSingleOrNull()
            ?: false
    }

    override suspend fun setInviteCodeUsed(usedByUserId: UserId, inviteCode: String) {
        client.sql("UPDATE FEEDSNG.invite_code SET used_by = :user_id WHERE invite_code = :invite_code")
            .bind("user_id", usedByUserId.id)
            .bind("invite_code", inviteCode)
            .await()
    }

}