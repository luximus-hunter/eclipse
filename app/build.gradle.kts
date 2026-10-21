plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningVariables = listOf(
    "ANDROID_KEYSTORE_FILE",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningVariables.associateWith {
    providers.environmentVariable(it).orNull
}
val hasReleaseSigningValues = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigningValues = releaseSigningValues.values.all { !it.isNullOrBlank() }

if (hasReleaseSigningValues && !hasCompleteReleaseSigningValues) {
    throw GradleException("All Android release signing environment variables must be set.")
}

android {
    namespace = "eu.tlipman.eclipse"
    compileSdk {
        version = release(37)
    }

    if (hasCompleteReleaseSigningValues) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_FILE"]))
                storePassword = requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["ANDROID_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["ANDROID_KEY_PASSWORD"])
            }
        }
    }

    defaultConfig {
        applicationId = "eu.tlipman.eclipse"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("VERSION_CODE").getOrElse("1").toInt()
        versionName = providers.gradleProperty("VERSION_NAME").getOrElse("1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasCompleteReleaseSigningValues) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("eu.tlipman.eclipse.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}