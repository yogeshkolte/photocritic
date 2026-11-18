// Measurement harness (design file 40): a JVM command-line tool that sends
// labelled photos through the :core clients. Author-run, not part of the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.yk.photocritic.harness.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaExec>("run") {
    // Paths on the command line are relative to the repository root.
    workingDir = rootProject.projectDir.parentFile
}

tasks.test {
    useJUnitPlatform()
}
