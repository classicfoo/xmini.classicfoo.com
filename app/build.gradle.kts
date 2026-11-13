plugins {
    id("com.android.application")
}

android {
    namespace = "com.classicfoo.xmini"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.classicfoo.xmini"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
