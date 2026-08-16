import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.hazemafaneh.babymonitorpro.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BabyMonitorPro"
            packageVersion = "1.0.0"
            description = "Watch and hear your baby from any device on the same WiFi."

            macOS {
                bundleID = "com.hazemafaneh.babymonitorpro"
                // A packaged macOS build is sandboxed: without these it can neither open
                // the camera nor bind the LAN port.
                entitlementsFile.set(project.file("entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>BabyMonitor Pro uses the camera to stream video to your other devices on this WiFi network.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>BabyMonitor Pro uses the microphone to stream sound from the nursery.</string>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>BabyMonitor Pro finds cameras on your home WiFi. Nothing is sent over the internet.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_babymonitorpro._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
