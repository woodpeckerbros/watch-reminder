plugins {
    id("com.android.application")
}

android {
    namespace = "com.woodpeckerbros.watchreminder.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.woodpeckerbros.watchreminder"
        minSdk = 26
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
    implementation("com.google.android.gms:play-services-wearable:18.0.0")
    implementation("com.kosherjava:zmanim:2.5.0")
}
