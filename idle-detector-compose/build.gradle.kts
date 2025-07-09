import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // id("module.publication")
    id("com.vanniktech.maven.publish") version "0.33.0"
}

android {
    namespace = "ke.co.banit.idle_detector_compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    buildFeatures {
        compose = true
    }
}

mavenPublishing {
    coordinates("io.github.angatiabenson", "idle-detector-compose", "0.0.5")

    pom {
        name.set("Idle Detector Compose")
        description.set("A Jetpack Compose library that detects user inactivity across your entire app with zero boilerplate. Perfect for implementing session timeouts, security screens, or automatic logouts.")
        inceptionYear.set("2025")
        url.set("https://github.com/angatiabenson/idle-detector-compose")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("angatiabenson")
                name.set("Angatia Benson")
                email.set("bensonetia@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/angatiabenson/idle-detector-compose")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Testing dependencies
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.ui.test.junit4)

    // Compose BOM manages versions of Compose libraries:
    implementation(platform(libs.androidx.compose.bom))

    // Core Compose libraries:
    implementation(libs.androidx.runtime)
    implementation(libs.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.work.runtime.ktx) // For background tasks
    implementation(libs.androidx.lifecycle.runtime.ktx) // For lifecycle awareness
    implementation(libs.androidx.lifecycle.runtime.compose) // For LocalLifecycleOwner
    testImplementation(kotlin("test"))

}