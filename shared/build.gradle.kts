import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqlDelight)
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BrickTown"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)

            // Ktor client (engine actuals live in androidMain / iosMain)
            api(libs.ktor.client.core)
            api(libs.ktor.client.resources)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.client.logging)
            api(libs.ktor.serialization.kotlinx.json)

            // DI
            api(libs.koin.core)

            // SQLDelight (mobile cache; drivers in androidMain / iosMain)
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines.extensions)

            // Date-time formatting helpers
            api(libs.kotlinx.datetime.ext)

            // Key-value storage (Android SharedPrefs / iOS NSUserDefaults)
            api(libs.multiplatform.settings.no.arg)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.core.ktx)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        // Runs on the JVM through `./gradlew test`; HTTP goes to a MockEngine.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        // Repository tests run on the real schema in an in-memory SQLite.
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

// The server the Android app talks to: -PapiBaseUrl=<url>. Debug falls back to
// the dev machine as the emulator sees it. Release has no fallback: the empty
// placeholder only lets `./gradlew test` compile the release variant, and
// :composeApp refuses to package a release unless the value is https://.
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").map { it.trimEnd('/') }

android {
    namespace = "ai.freeapp.bricktown.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.getOrElse("http://10.0.2.2:8080")}\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.getOrElse("")}\"")
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("ai.freeapp.bricktown.db")
        }
    }
}
