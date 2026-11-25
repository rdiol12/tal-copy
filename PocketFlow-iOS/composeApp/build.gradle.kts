// Thin module that produces the iOS framework. All app code lives in :shared;
// keeping it out of this module keeps it out of the generated Objective-C
// header, which only needs MainViewController().
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Function names in stack traces of release builds, for the crash report screen.
            freeCompilerArgs += "-Xadd-light-debug=enable"
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
    }
}
