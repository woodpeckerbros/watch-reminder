plugins {
    id("com.android.application")
}

android {
    namespace = "com.woodpeckerbros.watchreminder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.woodpeckerbros.watchreminder"
        minSdk = 30
        targetSdk = 35
        versionCode = 112
        versionName = "1.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
    implementation("androidx.health:health-services-client:1.1.0-rc02")
    implementation("com.google.guava:guava:33.7.1-android")
    implementation("com.kosherjava:zmanim:2.5.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
