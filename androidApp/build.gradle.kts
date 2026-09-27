import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    // Declared here as well as in :shared because the Live Update renderers live in this
    // module — they produce Android notification content, which :shared deliberately cannot
    // name — and :shared takes the library as an `implementation` dependency.
    implementation(libs.live.activities)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/**
 * Signing material for local release builds, read from the gitignored `local.properties`.
 *
 * A release APK has to be signed before a device will install it, and this project has no
 * committed key: what ships is signed with the release key, which lives nowhere near this
 * repository. So these four properties are optional. Set them on a machine that needs to put
 * a release build on a phone or the television; leave them unset everywhere else and
 * `assembleRelease` behaves exactly as it did before.
 *
 *     bmp.testKeystore=/path/to/keystore.jks
 *     bmp.testKeystorePassword=…
 *     bmp.testKeyAlias=…
 *     bmp.testKeyPassword=…
 *
 * Nothing secret is committed — `local.properties` is gitignored and the keystore lives
 * outside the repository. Generate a throwaway one with:
 *
 *     keytool -genkeypair -keystore ~/.android/babymonitorpro-testing.jks -alias testing \
 *       -keyalg RSA -keysize 2048 -validity 10000
 *
 * A build signed this way carries a different certificate from both the debug build and the
 * real release, so a device holding either has to have the app uninstalled before it will
 * install — which takes its settings with it.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val testKeystore = localProperties.getProperty("bmp.testKeystore")
    ?.let(::File)
    ?.takeIf { it.exists() }

android {
    namespace = "com.hazemafaneh.babymonitorpro"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hazemafaneh.babymonitorpro"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // Declared only when the keystore is actually present, so a checkout without one does
        // not fail configuration on a missing file.
        if (testKeystore != null) {
            create("local") {
                storeFile = testKeystore
                storePassword = localProperties.getProperty("bmp.testKeystorePassword")
                keyAlias = localProperties.getProperty("bmp.testKeyAlias")
                keyPassword = localProperties.getProperty("bmp.testKeyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Null where no local keystore is configured, which leaves the release build
            // unsigned exactly as before and leaves the real release signing untouched.
            signingConfig = signingConfigs.findByName("local")
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
    buildFeatures {
        compose = true
    }
}