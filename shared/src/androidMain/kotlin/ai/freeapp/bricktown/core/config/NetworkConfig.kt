package ai.freeapp.bricktown.core.config

import ai.freeapp.bricktown.shared.BuildConfig

/** Set per build type from -PapiBaseUrl, see `shared/build.gradle.kts`. */
actual object NetworkConfig {
    actual val BASE_URL: String = BuildConfig.API_BASE_URL
}
