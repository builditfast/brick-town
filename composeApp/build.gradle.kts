import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The iOS app links this one framework. It carries :shared inside it, so
    // iosApp must not also link Shared.framework: two static Kotlin frameworks
    // in one app are two Kotlin runtimes. Being static, it cannot carry the
    // SQLDelight driver's own link flag, so the iosApp target passes
    // -lsqlite3 itself (OTHER_LDFLAGS); without it the app fails at link.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            implementation(libs.androidx.glance.appwidget)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.qrose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(projects.shared)
        }
        // Runs on the JVM through `./gradlew test` (testDebugUnitTest), no device.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        // The screen smoke test (src/androidUnitTestDebug): Robolectric draws
        // every screen on the JVM, still under `./gradlew test`. Debug only,
        // because the activity it draws in comes from ui-test-manifest, a debug
        // dependency below.
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.kotlin.reflect)
            implementation(libs.junit)
        }
    }
}

// Release signing comes from the environment, filled through the secret rail
// (`secret request` / `env-set`), so no keystore is ever committed. Without all
// four the release still builds, unsigned: fine to inspect, refused by Play.
val releaseSigning = listOf(
    "ANDROID_KEYSTORE_FILE",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val missingReleaseSigning = releaseSigning.filterValues { it == null }.keys.toList()

android {
    namespace = "ai.freeapp.bricktown"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ai.freeapp.bricktown"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Robolectric reads the merged manifest and resources from here.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // `:composeApp:sourceSets` lists this path for debug, yet with Kotlin
        // 2.1.21 and AGP 8.11 the overlay is silently not merged unless it is
        // set here (resources under src/androidDebug/res merge either way).
        getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
    }
    signingConfigs {
        if (missingReleaseSigning.isEmpty()) {
            create("release") {
                storeFile = file(releaseSigning.getValue("ANDROID_KEYSTORE_FILE")!!)
                storePassword = releaseSigning.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("ANDROID_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            // R8 stays off: minified Ktor and kotlinx.serialization are untested on a device (spec issue 2).
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// A release must name its server: -PapiBaseUrl, https:// only, no default (the
// value itself is baked into BuildConfig by :shared). Wired to the packaging
// tasks, not to preReleaseBuild, because `./gradlew test` compiles the release
// variant for testReleaseUnitTest and must keep working with no property set.
// The unsigned warning lives here too: this task has no outputs, so it runs on
// every release package, while a doFirst on an UP-TO-DATE task would not.
val checkReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails a release package unless -PapiBaseUrl is an https:// URL; warns when unsigned."
    val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
    val missingSigning = missingReleaseSigning
    doLast {
        val url = apiBaseUrl.orNull
        val uri = url?.let { runCatching { URI(it) }.getOrNull() }
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            throw GradleException(
                "A release build needs -PapiBaseUrl set to the server's https:// address " +
                    "(got ${url ?: "no value"}). For example:\n" +
                    "  ./gradlew :composeApp:bundleRelease -PapiBaseUrl=https://api.example.com"
            )
        }
        if (missingSigning.isNotEmpty()) {
            logger.warn("WARNING: this release is UNSIGNED and Play will refuse it; set ${missingSigning.joinToString()} to sign it.")
        }
    }
}
tasks.named { it == "packageRelease" || it == "packageReleaseBundle" }
    .configureEach { dependsOn(checkReleaseConfig) }
tasks.named { it == "preReleaseBuild" }.configureEach { mustRunAfter(checkReleaseConfig) }

// kmp-doctor holds every bug that reached a phone: a control wired to {}, a Box
// painted to look like a Button, an inset nobody consumed, a keyboard covering
// the field. Running it after every debug APK makes repeating one of them fail
// the build, for whoever writes it, instead of relying on a skill being read.
// kmpDoctorSkip (gradle.properties) names the checks left out; the scaffold sets
// it for new apps. A machine without kmp-doctor builds as before, with one line.
val kmpDoctor by tasks.registering {
    group = "verification"
    description = "Runs kmp-doctor on this project; fails on any ERROR finding."
    val skip = providers.gradleProperty("kmpDoctorSkip").orElse("deps,server-host")
    val path = providers.environmentVariable("PATH").orElse("")
    val projectRoot = rootDir
    mustRunAfter("packageDebug")
    doLast {
        // An IDE started from the Dock or a launcher gets a PATH without
        // ~/.local/bin, where the freeapp install puts the tool, and would pass
        // every build unchecked.
        val places = path.get().split(File.pathSeparator) +
            listOf("${System.getProperty("user.home")}/.local/bin", "/root/.local/bin")
        val doctor = places
            .map { File(it, "kmp-doctor") }
            .firstOrNull { it.canExecute() }
        if (doctor == null) {
            logger.lifecycle("kmp-doctor: not installed, so the house checks did not run")
            return@doLast
        }
        val process = ProcessBuilder(doctor.path, projectRoot.path, "--skip", skip.get())
            .redirectErrorStream(true)
            .start()
        val report = process.inputStream.bufferedReader().readLines()
        val exit = process.waitFor()
        if (exit == 0) {
            report.filter { it.startsWith("[ warn") || it.startsWith("[allow") }.forEach { logger.lifecycle(it) }
            logger.lifecycle("kmp-doctor: clean")
            return@doLast
        }
        logger.lifecycle(report.filterNot { it.startsWith("[  ok") }.joinToString("\n"))
        val errors = report.count { it.startsWith("[ERROR") }
        if (errors == 0) {
            logger.lifecycle("kmp-doctor: failed to run (exit $exit)")
            throw GradleException("kmp-doctor exited $exit without a finding; its output is above.")
        }
        logger.lifecycle("kmp-doctor: $errors error(s)")
        throw GradleException(
            "kmp-doctor found $errors error(s) above, each naming the file to fix. " +
                "Fix the code; do not add the check to kmpDoctorSkip (now: ${skip.get()})."
        )
    }
}
tasks.named { it == "assembleDebug" }.configureEach { finalizedBy(kmpDoctor) }
// installDebug builds packageDebug and never assembleDebug, so without this the
// documented install command would put an APK on a phone the doctor never saw.
tasks.named { it == "installDebug" }.configureEach { dependsOn(kmpDoctor) }
