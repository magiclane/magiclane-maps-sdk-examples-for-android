plugins {
    id("com.magiclane.examples.sdk.gradle.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.magiclane.sdk.examples.basicshapedrawer"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.basicshapedrawer"

        minSdk = 26
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
    implementation(shared.androidx.media)
    implementation(shared.material)
    implementation(shared.androidx.navigation.fragment.ktx)
    implementation(shared.androidx.navigation.ui.ktx)

    testImplementation(shared.junit)
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
