// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

val verifyEngineIsPure by tasks.registering {
    description = "Enforces that :engine depends only on stdlib and serialization, and has zero android/androidx dependencies"
    group = "verification"
    doLast {
        val forbiddenPrefixes = listOf("com.google.android", "androidx.", "android.arch")
        configurations.named("runtimeClasspath").get().resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val group = artifact.moduleVersion.id.group
            val name = artifact.moduleVersion.id.name
            forbiddenPrefixes.forEach { forbidden ->
                if (group.startsWith(forbidden)) {
                    throw GradleException("Invariant violation (§2.7, §14): :engine must be pure Kotlin/JVM with no Android dependencies. Found forbidden dependency: $group:$name")
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyEngineIsPure)
}
