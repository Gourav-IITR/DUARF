// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.duarf.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.duarf.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The in-app language picker can switch to any UI language, so every language ships in the
    // base APK. Play's on-demand language download (Play Core) is a forbidden dependency (§14).
    bundle {
        language {
            enableSplit = false
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            val keystoreProperties = Properties().apply {
                FileInputStream(keystorePropertiesFile).use { load(it) }
            }
            create("release") {
                val storeFilePath = keystoreProperties.getProperty("storeFile") ?: ""
                val f = File(storeFilePath)
                storeFile = if (f.isAbsolute) {
                    f
                } else {
                    rootProject.file(storeFilePath)
                }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug") // Fallback for environments without release keys
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/assets/licenses"))
        }
    }

    buildFeatures {
        compose = true
    }
}

val generateLicenses by tasks.registering {
    description = "Generates build/generated/assets/licenses/licenses.json from releaseRuntimeClasspath POMs and enforces GPL-3.0 compatibility"
    group = "build"

    val outputDir = layout.buildDirectory.dir("generated/assets/licenses")
    outputs.dir(outputDir)

    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        val licensesFile = outDir.resolve("licenses.json")

        val config = configurations.named("releaseRuntimeClasspath").get()
        val componentIds = config.incoming.resolutionResult.allComponents
            .map { it.id }
            .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
            .distinctBy { "${it.group}:${it.module}:${it.version}" }
            .sortedBy { "${it.group}:${it.module}" }

        val result = dependencies.createArtifactResolutionQuery()
            .forComponents(componentIds)
            .withArtifacts(org.gradle.maven.MavenModule::class.java, org.gradle.maven.MavenPomArtifact::class.java)
            .execute()

        val licenseRegex = Regex("""<license>\s*<name>([^<]+)</name>""", RegexOption.DOT_MATCHES_ALL)
        val parentRegex = Regex("""<parent>\s*<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>\s*<version>([^<]+)</version>""", RegexOption.DOT_MATCHES_ALL)

        fun extractLicensesFromPom(pomFile: File): List<String> {
            val text = pomFile.readText()
            val lics = licenseRegex.findAll(text).map { it.groupValues[1].trim() }.toMutableList()
            if (lics.isNotEmpty()) return lics

            val parentMatch = parentRegex.find(text)
            if (parentMatch != null) {
                val pg = parentMatch.groupValues[1].trim()
                val pa = parentMatch.groupValues[2].trim()
                val pv = parentMatch.groupValues[3].trim()
                try {
                    val detached = configurations.detachedConfiguration(dependencies.create("$pg:$pa:$pv@pom"))
                    return extractLicensesFromPom(detached.singleFile)
                } catch (e: Exception) {
                    // ignore
                }
            }
            return emptyList()
        }

        fun isGplCompatible(lic: String): Boolean {
            val l = lic.lowercase()
            return l.contains("apache") || l.contains("mit") || l.contains("bsd") || l.contains("mpl") || l.contains("mozilla public license")
        }

        val jsonEntries = mutableListOf<String>()

        componentIds.forEach { id ->
            val compResult = result.resolvedComponents.find { it.id == id }
            val pomArtifact = compResult?.getArtifacts(org.gradle.maven.MavenPomArtifact::class.java)
                ?.filterIsInstance<org.gradle.api.artifacts.result.ResolvedArtifactResult>()
                ?.firstOrNull()

            val lics = if (pomArtifact != null) extractLicensesFromPom(pomArtifact.file) else emptyList()

            if (lics.isEmpty()) {
                throw GradleException("License missing for dependency: ${id.displayName}. Every runtime dependency must declare a valid license.")
            }

            lics.forEach { lic ->
                if (!isGplCompatible(lic)) {
                    throw GradleException("Incompatible license '$lic' found in dependency ${id.displayName}! Must be GPL-3.0-compatible (Apache-2.0, MIT, BSD, MPL).")
                }
            }

            val escapedLics = lics.joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }
            jsonEntries.add("""  {"group":"${id.group}","artifact":"${id.module}","version":"${id.version}","licenses":[$escapedLics]}""")
        }

        licensesFile.writeText("[\n" + jsonEntries.joinToString(",\n") + "\n]\n")
    }
}

val copyPacks by tasks.registering(Copy::class) {
    description = "Copies root packs/ into app/src/main/assets/packs/ at build time"
    from(rootProject.file("packs"))
    into(file("src/main/assets/packs"))
}

tasks.named("preBuild") {
    dependsOn(copyPacks, generateLicenses)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":capture"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.junit)
}

apply(from = rootProject.file("tools/ci/ci-checks.gradle.kts"))
