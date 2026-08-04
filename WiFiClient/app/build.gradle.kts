plugins {
    id("com.magiclane.sdk.examples.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.wificlient"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.wificlient"

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
    implementation(shared.material)

    testImplementation(shared.junit)
    androidTestImplementation(project(":build-testing"))
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.espresso.core)
}
