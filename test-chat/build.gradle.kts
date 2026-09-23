import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keyFile = System.getenv("JEV_KEYSTORE_PROPS")?.let { rootProject.file(it) }
    ?: rootProject.file(".signing/keystore.properties")
val keys = Properties().apply { if (keyFile.isFile) keyFile.inputStream().use { load(it) } }

android {
    namespace = "io.github.qq244901796.chathelp.testchat"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.qq244901796.chathelp.testchat"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (keys.isNotEmpty()) create("release") {
            storeFile = keys.getProperty("storeFile")?.let {
                File(it).let { path -> if (path.isAbsolute) path else File(keyFile.parentFile, it) }
            }
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(":app:checkReleaseSigning")
}

tasks.register("exportRuntimeArtifacts") {
    doLast {
        val output = layout.buildDirectory.file("compliance/runtime-artifacts.tsv").get().asFile
        output.parentFile.mkdirs()
        output.writeText(configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
            .joinToString("\n") { "${it.moduleVersion.id}\t${it.file.absolutePath}" })
    }
}
