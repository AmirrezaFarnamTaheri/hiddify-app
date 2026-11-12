plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../../android/app/src/main/kotlin")
        kotlin.include("**/InterfaceUpdateRetry.kt", "**/PendingNetworkRequests.kt", "**/NetworkCallbackEpoch.kt")
    }
    test {
        kotlin.srcDir("../../android/app/src/test/kotlin")
        kotlin.include("**/InterfaceUpdateRetryTest.kt", "**/PendingNetworkRequestsTest.kt", "**/NetworkCallbackEpochTest.kt")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
