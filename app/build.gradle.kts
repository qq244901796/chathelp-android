import java.io.FileInputStream
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Local signing material is ignored by Git. An external properties file can
// still be selected through JEV_KEYSTORE_PROPS. Never publish an unsigned APK.
val releasePropsFile = System.getenv("JEV_KEYSTORE_PROPS")?.let { rootProject.file(it) }
    ?: rootProject.file(".signing/keystore.properties")
val releaseProps = Properties().apply {
    if (releasePropsFile.isFile) FileInputStream(releasePropsFile).use { load(it) }
}
val releaseStoreFile = releaseProps.getProperty("storeFile")?.let {
    val path = File(it)
    if (path.isAbsolute) path else File(releasePropsFile.parentFile, it)
}

android {
    namespace = "com.jev.probe"
    compileSdk = 35
    // Use -PtestBuildType=release to run the existing tests against the signed APK.
    testBuildType = providers.gradleProperty("testBuildType").orElse("debug").get()

    defaultConfig {
        applicationId = "com.jev.probe"
        minSdk = 30
        targetSdk = 35
        versionCode = 7
        versionName = "1.4.2-glm"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ML Kit's bundled Chinese recognizer ships native libs for every ABI.
        // The target phone (and every phone this can run on: minSdk 30) is
        // arm64, so keep only that one — the other three are dead weight.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (releaseProps.isNotEmpty()) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".glm"
            resValue("string", "app_name", "ChatHelp 智谱助手")
        }
        release {
            applicationIdSuffix = ".glm.release"
            resValue("string", "app_name", "ChatHelp 智谱助手")
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Uncompressed, page-aligned .so files: required for the 16 KB page-size
    // devices Android 15+ ships, and it lets the loader mmap the ML Kit natives
    // instead of unpacking them at install time.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val checkReleaseSigning by tasks.registering {
    doLast {
        check(listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
            .all { !releaseProps.getProperty(it).isNullOrBlank() } && releaseStoreFile?.isFile == true) {
            "Release signing is missing. Run python scripts/prepare_release_signing.py or set JEV_KEYSTORE_PROPS."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseSigning)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // On-device OCR. The *bundled* Chinese model (not the play-services variant):
    // it works on phones with no Google Play services and needs no model download.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Machine paths are written only to ignored build output, never to the public inventory.
tasks.register("exportRuntimeArtifacts") {
    doLast {
        val output = layout.buildDirectory.file("compliance/runtime-artifacts.tsv").get().asFile
        output.parentFile.mkdirs()
        output.writeText(configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
            .joinToString("\n") { "${it.moduleVersion.id}\t${it.file.absolutePath}" })
        println("Runtime inventory: ${output.name}")
    }
}
