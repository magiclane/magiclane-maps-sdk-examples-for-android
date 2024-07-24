plugins {
    id("com.magiclane.examples.sdk.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.senddebuginfo"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.senddebuginfo"

        minSdk = shared.versions.minSdkVersion.get().toInt()
        targetSdk = shared.versions.targetSdkVersion.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(shared.magiclane.maps.kotlin)

    implementation(shared.androidx.core.ktx)
    implementation(shared.androidx.activity.ktx)
    implementation(shared.androidx.appcompat)
    implementation(shared.material)
    implementation(shared.jetbrains.kotlinx.coroutines.android)
    implementation(shared.jetbrains.kotlinx.coroutines.core)

    testImplementation(shared.junit)
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
