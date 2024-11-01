plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.yuxiang.drawer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yuxiang.drawer"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
}