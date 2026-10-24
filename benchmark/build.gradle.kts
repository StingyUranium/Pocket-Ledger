plugins { alias(libs.plugins.android.test) }

android {
    namespace = "in.thomas.pocketledger.benchmark"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// No benchmark install/test variant exists for the personal application ID.
val isolated = providers.gradleProperty("ledger.testApplicationId").orNull == "in.thomas.pocketledger.validation"
androidComponents.beforeVariants { it.enable = isolated && it.buildType == "benchmark" }

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}
