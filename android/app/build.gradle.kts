// Android application module (design file 17).
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Release signing material stays outside git (file 17). Without
// keystore.properties, release builds are unsigned (CI).
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

android {
    namespace = "com.yk.photocritic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yk.photocritic"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") { dimension = "env" }
        create("prod") { dimension = "env" }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Tests read shared/ from the repository (file 39).
        unitTests.all { it.systemProperty("pc.repoRoot", rootProject.projectDir.parentFile.absolutePath) }
    }

    // Device checks read the shared test images (Phase 0 step 6, file 50).
    sourceSets["androidTest"].assets.directories += "../../shared/images"
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// Fails on any permission in the merged release manifest outside the
// allowlist (file 17). The list was confirmed from the merged manifest on
// 2026-11-17.
tasks.register("checkPermissions") {
    group = "verification"
    description = "Fails if the merged prodRelease manifest has a permission outside the allowlist."
    dependsOn("processProdReleaseMainManifest")
    val allowedPermissions =
        setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
            "com.yk.photocritic.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
    val manifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifest/prodRelease/processProdReleaseMainManifest/AndroidManifest.xml",
        )
    inputs.file(manifest)
    doLast {
        val text = manifest.get().asFile.readText()
        val found =
            Regex("""<uses-permission(?:-sdk-23)?\s+android:name="([^"]+)"""")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet()
        val extra = found - allowedPermissions
        println("Merged permissions: ${found.sorted().joinToString()}")
        check(extra.isEmpty()) { "Permissions outside the allowlist: ${extra.sorted().joinToString()}" }
    }
}
