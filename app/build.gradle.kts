plugins {
    id("com.android.application")
}

android {
    namespace = "com.woodpeckerbros.watchreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.woodpeckerbros.watchreminder"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")
    implementation("androidx.health:health-services-client:1.1.0-rc01")
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("com.kosherjava:zmanim:2.5.0")
    implementation("com.google.android.gms:play-services-wearable:18.0.0")
}
