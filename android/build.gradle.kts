// Shared build setup for :core and :app (design file 16).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt.yml"))
    }

    // detekt 1.23 runs on the Kotlin version it was built with.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
        }
    }
}

// Only core/net may use OkHttp directly (design §2, file 20). detekt allows one
// ForbiddenImport list per project, so this rule is a separate check.
tasks.register("checkLayering") {
    group = "verification"
    description = "Fails if okhttp3 is imported outside core/net."
    val sources = fileTree(projectDir) {
        include("**/src/main/kotlin/**/*.kt")
        exclude("core/src/main/kotlin/com/yk/photocritic/core/net/**")
    }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.filter { file -> file.readLines().any { it.startsWith("import okhttp3") } }
        check(offenders.isEmpty()) { "okhttp3 imported outside core/net: ${offenders.joinToString()}" }
    }
}
