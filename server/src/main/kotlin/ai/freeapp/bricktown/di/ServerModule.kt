package ai.freeapp.bricktown.di

import ai.freeapp.bricktown.auth.AccessTokens
import ai.freeapp.bricktown.auth.AccountService
import ai.freeapp.bricktown.auth.PasswordHasher
import ai.freeapp.bricktown.auth.RefreshTokenRepository
import ai.freeapp.bricktown.auth.UserRepository
import ai.freeapp.bricktown.config.AppConfig
import ai.freeapp.bricktown.flags.loadFeatureFlags
import ai.freeapp.bricktown.town.TownRepository
import org.koin.dsl.module

/**
 * EXTENSION POINT: register new server-side singletons here.
 * One Koin binding per repository / use-case.
 */
fun serverModule(config: AppConfig) = module {
    single { config.legal }
    single { AccessTokens(config.jwtSecret) }
    single { PasswordHasher() }
    single { UserRepository() }
    single { RefreshTokenRepository() }
    single { AccountService(get(), get(), get(), get()) }
    single { TownRepository() }
    single { loadFeatureFlags(config.flagsStatePath) }
}
