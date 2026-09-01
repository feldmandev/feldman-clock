import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
}

/**
 * Local, untracked configuration. See keystore.properties.example for the expected keys.
 *
 * Release signing credentials and the GeoNames API username live here so they never reach
 * version control. Debug builds work without the file; release builds fail fast if it is absent.
 */
val localConfig = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localConfigOrNull(key: String): String? =
    (localConfig.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

fun requireLocalConfig(key: String): String = localConfigOrNull(key)
    ?: throw GradleException(
        "Missing '$key'. Copy keystore.properties.example to keystore.properties and fill it in, " +
            "or set the $key environment variable. See README.md > Building."
    )

val hasReleaseSigning = localConfigOrNull("STORE_FILE") != null

// Names the .aab produced by `bundleRelease`; outputFileName below only affects APKs.
base {
    archivesName.set("Clock")
}

androidComponents {
    val commitNumber = 1
    val buildCommit = "0000000"

    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {

                val fileNameProvider = output.versionName.map { versionName ->
                    if (variant.buildType == "nightly") {
                        "Clock_${versionName}-nightly-${commitNumber}-${buildCommit}.apk"
                    } else {
                        "Clock_${versionName}-release.apk"
                    }
                }

                output.outputFileName.set(fileNameProvider)
            }
        }
    }
}

configure<ApplicationExtension> {
    namespace = "com.feldman.clock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.feldman.clock"
        minSdk = 34
        targetSdk = 37

        // versionCode scheme: major * 10000 + minor * 100 + patch.
        // 1.0.0 -> 10000, 1.0.1 -> 10001, 1.1.0 -> 10100, 2.0.0 -> 20000.
        // Play requires this to strictly increase on every upload; bump it with versionName.
        versionCode = 10000
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }


    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireLocalConfig("STORE_FILE"))
                storePassword = requireLocalConfig("STORE_PASSWORD")
                keyAlias = requireLocalConfig("KEY_ALIAS")
                keyPassword = requireLocalConfig("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            manifestPlaceholders["appName"] = "@string/app_label"
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Lets contributors run `assembleRelease` without the private keystore; the
                // artifact is unsigned and cannot be published.
                logger.warn("No keystore.properties found - release build will be unsigned.")
                null
            }
            buildConfigField("boolean", "IS_DEBUG_BUILD", "false")
            buildConfigField("String", "COMMIT_NUMBER", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            manifestPlaceholders["appName"] = "@string/app_label_debug"
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
            buildConfigField("String", "COMMIT_NUMBER", "\"\"")
        }
    }

    lint {
        abortOnError = false
        // These block a Play release; everything else stays advisory so the huge backlog of
        // translation warnings does not hide them.
        fatal += listOf(
            "QueryAllPackagesPermission",
            "InsecureBaseConfiguration",
            "AcceptsUserCertificates",
            "MissingClass",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    ndkVersion = "27.2.12479018"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":alarm-ui"))
    implementation(libs.androidx.compose.ui.graphics)
    // Android Views and platform integration
    implementation(libs.material)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // App architecture and navigation
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.kotlinx.serialization.core)

    // Data and networking
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Media
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // UI components
    implementation(libs.hsv.alpha.color.picker.android)
    implementation(libs.motion)

    // Instrumented tests
    androidTestImplementation(libs.testing)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
}
