plugins {
    id("com.android.application")
}

android {
    namespace = "com.woodpeckerbros.watchreminder.guardian"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.woodpeckerbros.watchreminder.guardian"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
