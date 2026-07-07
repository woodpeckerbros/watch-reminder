plugins {
    id("com.android.application") version "9.2.1" apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "androidx.core:core:1.13.1",
            "androidx.versionedparcelable:versionedparcelable:1.1.1",
            "androidx.lifecycle:lifecycle-runtime:2.6.2",
            "androidx.lifecycle:lifecycle-viewmodel:2.6.2"
        )
    }
}
