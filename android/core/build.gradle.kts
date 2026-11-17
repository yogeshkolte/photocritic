// Pure Kotlin (JVM) module: providers, network, session, config, consent and
// report logic. No Android or Firebase dependency (design §2, file 21).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        // The prompts live once, in shared/, for both apps (file 11).
        resources.srcDir(rootProject.file("../shared/prompts"))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    useJUnitPlatform()
    // Tests read shared/ from the repository (file 39).
    systemProperty("pc.repoRoot", rootProject.projectDir.parentFile.absolutePath)
}

kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
