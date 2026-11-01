import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Upload-key credentials live outside the repo. Point SARALMT_KEYSTORE_PROPERTIES at a
// keystore.properties file (storeFile is resolved relative to it); CI writes one from secrets.
val keystorePropsFile = System.getenv("SARALMT_KEYSTORE_PROPERTIES")?.let(::File)
val keystoreProps = Properties().apply {
    if (keystorePropsFile?.exists() == true) keystorePropsFile.inputStream().use(::load)
}

android {
    namespace = "com.saraltechies.saralmoneytracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.saraltechies.saralmoneytracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = keystorePropsFile!!.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // BiometricPrompt (app lock) needs a FragmentActivity, and Play's in-app update library brings an old Fragment.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    // QR codes for "scan to pay" on loan statements. Pure Java, no network.
    implementation(libs.zxing.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Google Play in-app review and update prompts. They talk to the Play Store app, not the internet.
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
