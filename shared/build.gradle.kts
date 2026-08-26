import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Applied explicitly: the manual dependsOn edges below (hostMain) would otherwise
    // switch the default hierarchy off and orphan iosMain/appleMain.
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
       namespace = "com.hazemafaneh.babymonitorpro.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        // Broadcaster-capable targets. The embedded Ktor server, platform capture and
        // mDNS live here so none of it is dragged into the browser bundle — the web
        // target is viewer-only.
        val hostMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.contentNegotiation)
            }
        }
        // Android and desktop share the JVM library surface (java.net, javax.sound),
        // so anything identical on both lives here rather than being written twice.
        val jvmAndroidMain by creating { dependsOn(hostMain) }
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmMain.get().dependsOn(jvmAndroidMain)
        // The two targets with a system-owned live surface — iOS Live Activities and
        // Android 16 Live Updates. The library is published for android and ios* only, so
        // this is the widest source set that can see it; desktop and the browser take the
        // no-op LiveSessions from commonMain instead.
        val mobileMain by creating {
            dependsOn(hostMain)
            dependencies {
                implementation(libs.live.activities)
            }
        }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
            implementation(libs.koin.android)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcodeScanning)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.jmdns)
            implementation(libs.webcam.capture)
        }
        iosMain.dependencies {
            // CIO, not Darwin: NSURLSession parses `multipart/x-mixed-replace` itself and
            // Ktor's delegate does not acknowledge the per-part responses, which loses
            // bytes mid-frame. See client/CameraHttpClient.ios.kt.
            implementation(libs.ktor.client.cio)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
