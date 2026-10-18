plugins {
    id("com.android.application")
}

android {
    namespace = "com.duddex.faderling"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.duddex.faderling"
        minSdk = 22
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
