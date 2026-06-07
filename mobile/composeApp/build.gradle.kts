import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("com.google.gms.google-services")
}

val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.reader().use { load(it) }
    else {
        setProperty("versionName", "1.0.0")
        setProperty("versionCode", "1")
    }
}

val generateVersionFile by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version/kotlin")
    outputs.dir(outputDir)
    val vName = versionProps.getProperty("versionName")
    val vCode = versionProps.getProperty("versionCode")
    doLast {
        val dir = outputDir.get().asFile
        val pkgDir = dir.resolve("org/messenger/app/generated")
        pkgDir.mkdirs()
        pkgDir.resolve("AppVersion.kt").writeText(
            """
            package org.messenger.app.generated

            object AppVersion {
                const val VERSION_NAME = "$vName"
                const val VERSION_CODE = $vCode
            }
            """.trimIndent()
        )
    }
}

val generateJvmVersionResource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/jvmVersionRes")
    outputs.dir(outputDir)
    val vName = versionProps.getProperty("versionName")
    val vCode = versionProps.getProperty("versionCode")
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("version.properties").writeText(
            """
            VERSION_NAME=$vName
            VERSION_CODE=$vCode
            """.trimIndent()
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/version/kotlin"))
        }
    }
}

tasks.matching { it.name.startsWith("compile") || it.name.contains("Kotlin") }
    .configureEach { dependsOn(generateVersionFile) }

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(projects.shared)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
            implementation(libs.kotlinx.serialization.json)
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
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            resources.srcDir(layout.buildDirectory.dir("generated/jvmVersionRes"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.multiplatformSettings)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        iosMain.dependencies {
            api(projects.shared)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettings)
        }
    }
}

android {
    namespace = "org.messenger.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.messenger.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.messenger.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.messenger.app"
            packageVersion = versionProps.getProperty("versionName")

            modules(
                "java.net.http",
                "java.naming",
                "java.sql",
                "jdk.crypto.ec",
                "jdk.unsupported"
            )
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
    }
}

// в”Ђв”Ђ Bind jvm version.properties generation to processResources в”Ђв”Ђ
tasks.matching { it.name == "jvmProcessResources" }
    .configureEach { dependsOn(generateJvmVersionResource) }

// в”Ђв”Ђ Portable zip РґР»СЏ Р°РІС‚Рѕ-РѕР±РЅРѕРІР»РµРЅРёСЏ (Р·Р°Р»РёРІР°РµС‚СЃСЏ РІ S3) в”Ђв”Ђ
val packagePortableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    val appName = "org.messenger.app" // == compose.desktop packageName
    val distDir = layout.buildDirectory.dir("compose/binaries/main/app/$appName")
    from(distDir)

    val osTag = when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "win"
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "mac"
        else -> "linux"
    }
    val vName = versionProps.getProperty("versionName")
    archiveFileName.set("messenger-$vName-$osTag.zip")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
}

// в”Ђв”Ђ Print sha256 + size РґР»СЏ РїСѓР±Р»РёРєР°С†РёРё СЂРµР»РёР·Р° в”Ђв”Ђ
val printPortableChecksum by tasks.registering {
    dependsOn(packagePortableZip)
    val zipFileProvider = packagePortableZip.flatMap { it.archiveFile }
    doLast {
        val zip = zipFileProvider.get().asFile
        val md = MessageDigest.getInstance("SHA-256")
        zip.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (true) {
                r = ins.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        val sha = md.digest().joinToString(separator = "") { b -> ((b.toInt() and 0xff) + 0x100).toString(16).substring(1) }
        println("FILE=" + zip.name)
        println("SIZE_BYTES=" + zip.length())
        println("SHA256=" + sha)
    }
}