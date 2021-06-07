package sh.weller.feedsng.web.support

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.reactor.asMono
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserQueryService

@Service
class SpringUserDetailsServiceWrapper(
    private val userQueryService: UserQueryService
) : ReactiveUserDetailsService {

    val scope = CoroutineScope(Dispatchers.Default)

    override fun findByUsername(username: String): Mono<UserDetails> =
        scope.async { userQueryService.getUserByUsername(username)?.toSpringUserDetailsWrapper() }
            .asMono(Dispatchers.Default)
            .switchIfEmpty(Mono.error(UsernameNotFoundException("A user with name $username does not exist.")))
}

data class SpringUserDetailsWrapper(
    private val username: String,
    private val password: String,
    private val isEnabled: Boolean,
    private val isLocked: Boolean
) : UserDetails {

    override fun getPassword(): String = password
    override fun getUsername(): String = username
    override fun isAccountNonLocked(): Boolean = isLocked.not()
    override fun isEnabled(): Boolean = isEnabled

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = mutableListOf()
    override fun isAccountNonExpired(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
}

private fun User.toSpringUserDetailsWrapper(): UserDetails =
    SpringUserDetailsWrapper(
        username = userData.username,
        password = userData.passwordHash,
        isEnabled = userData.isEnabled,
        isLocked = userData.isLocked
    )