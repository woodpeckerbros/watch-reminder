plugins {
    id("com.android.application")
}

android {
    namespace = "com.woodpeckerbros.watchreminder.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.woodpeckerbros.watchreminder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1018
        versionName = "1.18"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("com.kosherjava:zmanim:2.5.0")
    implementation("com.android.billingclient:billing:9.1.0")
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }
}
