plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin shared by the app (through :shared) and the server, so a total the
// phone shows and a total the voice agent reads come from one function. No
// Android target: the Android build consumes the jvm variant, as it does for
// kotlinx-coroutines.
kotlin {
    jvmToolchain(17)
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
