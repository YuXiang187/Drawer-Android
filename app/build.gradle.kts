plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.yuxiang.drawer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuxiang.drawer"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    /*
    tasks {
        withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:deprecation")
        }
    }
     */
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
}