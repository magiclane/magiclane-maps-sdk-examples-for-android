plugins {
    id("com.magiclane.sdk.examples.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.importgeojsonmarkers"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.importgeojsonmarkers"

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
    implementation(shared.androidx.appcompat)
    implementation(shared.androidx.espresso.idlingresource)
    implementation(shared.androidx.lifecycle.runtime.ktx)
    implementation(shared.jetbrains.kotlinx.coroutines.android)
    implementation(shared.material)
    implementation(shared.androidx.recyclerview)

    testImplementation(shared.junit)
    androidTestImplementation(project(":build-testing"))
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
}
