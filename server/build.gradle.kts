import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

group = "ai.freeapp.bricktown"
version = "1.0.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

kotlin {
    jvmToolchain(17)
}

// The fat JAR smoke lives in its own source set so embedded Postgres never
// reaches the shipped JAR (its per-platform binaries total about 190 MB).
val smoke: SourceSet by sourceSets.creating

dependencies {
    implementation(project(":domain"))
    implementation(libs.logback)

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Accounts
    implementation(libs.bcrypt)

    // Reads the words of a PDF bank statement, in memory only
    implementation(libs.pdfbox)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Test
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.embedded.postgres)
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
    testRuntimeOnly(libs.embedded.postgres.binaries.darwin.arm64)
    testRuntimeOnly(libs.embedded.postgres.binaries.linux.arm64)

    // Fat JAR smoke
    "smokeImplementation"(libs.kotlinx.serialization.json)
    "smokeImplementation"(libs.postgres)
    "smokeImplementation"(libs.embedded.postgres)
    "smokeImplementation"(platform(libs.embedded.postgres.binaries.bom))
    "smokeRuntimeOnly"(libs.embedded.postgres.binaries.darwin.arm64)
    "smokeRuntimeOnly"(libs.embedded.postgres.binaries.linux.arm64)
    "smokeRuntimeOnly"(libs.logback)
}

// Boots the JAR that shadowJar just wrote against a throwaway Postgres and runs
// register, GET /me, DELETE /me over HTTP. Compiling and unit tests cannot see
// the failure this exists for: a JAR whose Flyway finds no migrations starts
// cleanly and answers 500 on the first insert.
val shadowJarSmoke by tasks.registering(JavaExec::class) {
    description = "Starts the fat JAR on embedded Postgres and checks the account API end to end."
    group = "verification"
    classpath = smoke.runtimeClasspath
    mainClass.set("ai.freeapp.bricktown.smoke.FatJarSmokeKt")

    val jar = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    inputs.file(jar)
    argumentProviders.add(CommandLineArgumentProvider { listOf(jar.get().asFile.absolutePath) })
    isIgnoreExitValue = true

    doLast {
        if (executionResult.get().exitValue != 0) {
            // A failed JAR must not be left where `scp` or a deploy script would
            // pick it up.
            val jarFile = jar.get().asFile
            jarFile.delete()
            throw GradleException(
                "Fat JAR smoke FAILED (see the 'smoke:' lines above). " +
                    "${jarFile.name} was deleted so it cannot be deployed. " +
                    "Fix the server, then run :server:shadowJar again."
            )
        }
    }
}

// Flyway finds its database support and its SQL migration resolver through
// META-INF/services files spread over several jars. Unmerged, the fat JAR keeps
// only one of them: the server then starts cleanly, logs "migrations did not
// follow the filename convention", and runs against an empty schema (task 830).
tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    finalizedBy(shadowJarSmoke)
    // Gradle's file-system cache misses the smoke's delete and would call this
    // task UP-TO-DATE with no JAR on disk, so the real file is checked.
    outputs.upToDateWhen { archiveFile.get().asFile.isFile }
}
