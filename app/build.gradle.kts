import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing key, kept outside the repository. Without it, release builds are produced unsigned.
val signingFile = file(
    providers.gradleProperty("faderling.signing").orNull
        ?: "${System.getProperty("user.home")}/.android/faderling-release.properties"
)
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use(::load) }

android {
    namespace = "com.duddex.faderling"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.duddex.faderling"
        minSdk = 22
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        if (signingFile.exists()) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
