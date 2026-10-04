plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** Google's public AdMob test IDs: always safe to load, never pay out. */
val TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
val TEST_ADMOB_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "nl.bluecard.app.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.bluecard.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // AdMob: Google's public test IDs unless real ones are set (see README, "Advertenties").
        manifestPlaceholders["admobAppId"] = TEST_ADMOB_APP_ID
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$TEST_ADMOB_BANNER_ID\"")
        buildConfigField("String", "ADMOB_HOME_BANNER_ID", "\"$TEST_ADMOB_BANNER_ID\"")
    }

    // Play Store upload key: set these four in ~/.gradle/gradle.properties (never in git), see docs/PLAY_STORE.md.
    val uploadStore = providers.gradleProperty("bluecard.upload.storeFile").orNull
    val upload = if (uploadStore != null) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = providers.gradleProperty("bluecard.upload.storePassword").get()
            keyAlias = providers.gradleProperty("bluecard.upload.keyAlias").get()
            keyPassword = providers.gradleProperty("bluecard.upload.keyPassword").get()
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // With an upload key configured the release is ready for Play; otherwise it is signed with the debug
            // key, which is fine for installing on test phones but is refused by the Play Console.
            signingConfig = upload ?: signingConfigs.getByName("debug")
            // Real ad IDs only in release builds, so debug builds never show (and click) live ads.
            val appId = providers.gradleProperty("bluecard.admob.appId").orNull
            val bannerId = providers.gradleProperty("bluecard.admob.bannerId").orNull
            val homeBannerId = providers.gradleProperty("bluecard.admob.homeBannerId").orNull ?: bannerId
            if (appId != null && bannerId != null) {
                manifestPlaceholders["admobAppId"] = appId
                buildConfigField("String", "ADMOB_BANNER_ID", "\"$bannerId\"")
                buildConfigField("String", "ADMOB_HOME_BANNER_ID", "\"$homeBannerId\"")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // The language can be chosen inside the app, so every install needs all translations.
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Android Auto view for the host (debug builds only, see src/debug/AndroidManifest.xml).
    debugImplementation(libs.androidx.car.app)

    testImplementation(libs.junit)
}
