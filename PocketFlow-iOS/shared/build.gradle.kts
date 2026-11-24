plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The JDK/Android look-alikes are shared with the WorkHours port.
val compat = "../../WorkHours-iOS/shared/src"

kotlin {
    // JVM target: runs the unit tests on a Linux CI runner.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
        }
        commonMain {
            kotlin.srcDir("$compat/commonMain/kotlin/compat")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.compose.ui.backhandler)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        iosMain { kotlin.srcDir("$compat/iosMain/kotlin/compat") }
        jvmMain {
            kotlin.srcDir("$compat/jvmMain/kotlin/compat")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Unit tests write their data files under build/ instead of the user's home folder.
tasks.withType<Test>().configureEach {
    systemProperty("workhours.dataDir", layout.buildDirectory.dir("test-data").get().asFile.path)
}
