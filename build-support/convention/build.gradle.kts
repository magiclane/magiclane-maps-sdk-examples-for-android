import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

repositories {
	google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly(libs.build.android.gradle)
    compileOnly(libs.build.kotlin)
    compileOnly(libs.detekt.gradle)
    compileOnly(libs.ktlint.gradle)
}

gradlePlugin {
    plugins {
        register("AndroidKtlint") {
            id = "com.magiclane.examples.sdk.gradle.ktlint"
            implementationClass = "GradleKtlintConventionPlugin"
        }

        register("AndroidDetekt") {
            id = "com.magiclane.examples.sdk.gradle.detekt"
            implementationClass = "DetektConventionPlugin"
        }

        register("MagicLaneExamplesApplication") {
            id = "com.magiclane.examples.sdk.gradle.application"
            implementationClass = "ApplicationModulePlugin"
        }
    }
}

