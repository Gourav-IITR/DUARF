// Privacy and Security Invariant Verification Tasks (§2, §14)

val verifyDependencies by tasks.registering {
    description = "Enforces that the release build contains no forbidden dependencies (analytics, ads, network clients, crash SDKs)"
    group = "verification"
    doLast {
        val forbiddenGroups = listOf(
            "com.google.firebase",
            "com.google.android.gms",
            "com.google.mlkit",
            "com.google.android.play",
            "io.sentry",
            "com.facebook",
            "com.appsflyer",
            "com.amplitude",
            "com.mixpanel"
        )
        val forbiddenKeywords = listOf("okhttp", "retrofit", "ktor-client")

        val components = project.configurations.named("releaseRuntimeClasspath").get().incoming.resolutionResult.allComponents
        components.forEach { component ->
            val id = component.id
            val idStr = id.displayName.lowercase()
            
            forbiddenGroups.forEach { forbidden ->
                if (idStr.startsWith(forbidden) || idStr.contains(":$forbidden")) {
                    throw GradleException("Invariant violation (§2.2, §14): Forbidden dependency group detected: $idStr")
                }
            }

            forbiddenKeywords.forEach { kw ->
                if (idStr.contains(kw)) {
                    throw GradleException("Invariant violation (§2.2, §14): Forbidden network/client library detected: $idStr")
                }
            }
        }
    }
}

val verifyPermissions by tasks.registering {
    description = "Enforces that the release merged manifest contains zero network permissions and only allowlisted permissions"
    group = "verification"
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifestFile = file("${layout.buildDirectory.get()}/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
        if (!manifestFile.exists()) {
            throw GradleException("Merged manifest not found at: ${manifestFile.absolutePath}")
        }

        val content = manifestFile.readText()
        val permissionRegex = Regex("""<uses-permission\s+android:name="([^"]+)"""")
        val foundPermissions = permissionRegex.findAll(content).map { it.groupValues[1] }.toSet()

        val allowedPermissions = setOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.VIBRATE",
            "com.duarf.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )

        val forbiddenPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE"
        )

        foundPermissions.forEach { perm ->
            if (forbiddenPermissions.contains(perm)) {
                throw GradleException("CRITICAL Invariant 1 violation (§2.1, §14): Network permission '$perm' found in release manifest!")
            }
            if (!allowedPermissions.contains(perm)) {
                throw GradleException("Invariant violation (§14): Unallowlisted permission '$perm' found in release manifest!")
            }
        }
    }
}

val verifyExportedComponents by tasks.registering {
    description = "Enforces that only permitted components are exported in the release manifest"
    group = "verification"
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifestFile = file("${layout.buildDirectory.get()}/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
        val content = manifestFile.readText()

        val allowedExportedClasses = setOf(
            "com.duarf.app.MainActivity",
            "com.duarf.capture.share.CheckMessageActivity",
            "com.duarf.capture.notification.WaNotificationListener"
        )

        val componentRegex = Regex("""<(activity|service|receiver|provider)[^>]*android:name="([^"]+)"[^>]*android:exported="true"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val matches = componentRegex.findAll(content)

        matches.forEach { match ->
            val compType = match.groupValues[1]
            val compName = match.groupValues[2]
            val fqdn = if (compName.startsWith(".")) "com.duarf.app$compName" else compName
            if (!allowedExportedClasses.contains(fqdn)) {
                throw GradleException("Invariant violation (§14): Exported $compType '$fqdn' is not in the permitted exported components list.")
            }
        }
    }
}

val verifyNoContentLogging by tasks.registering {
    description = "Enforces that no message content or raw logging APIs are called outside SafeLog"
    group = "verification"
    doLast {
        val targetDirs = listOf(
            rootProject.file("engine/src"),
            rootProject.file("capture/src"),
            rootProject.file("data/src")
        )

        val forbiddenLogPatterns = listOf(
            Regex("""android\.util\.Log\.[vdiwe]\s*\("""),
            Regex("""System\.out\.print"""),
            Regex("""\bprintln\s*\("""),
            Regex("""Timber\.[vdiwe]\s*\(""")
        )

        targetDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.forEach { file ->
                    if (!file.name.contains("SafeLog")) {
                        val lines = file.readLines()
                        lines.forEachIndexed { index, line ->
                            forbiddenLogPatterns.forEach { pattern ->
                                if (pattern.containsMatchIn(line)) {
                                    throw GradleException("Invariant violation (§2.4, §14): Forbidden raw logging found in ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '$line'")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

val verifyNoDebugToolsInRelease by tasks.registering {
    description = "Enforces that release builds contain no debug tools such as NotificationRecorder"
    group = "verification"
    doLast {
        // 1. Check capture release library classes JAR if it exists
        val captureJar = rootProject.file("capture/build/intermediates/runtime_library_classes_jar/release/bundleLibRuntimeToJarRelease/classes.jar")
        if (captureJar.exists()) {
            val jarFile = java.util.jar.JarFile(captureJar)
            val forbidden = jarFile.entries().asSequence().any { it.name.contains("NotificationRecorder") }
            jarFile.close()
            if (forbidden) {
                throw GradleException("Invariant violation: NotificationRecorder found in capture release JAR!")
            }
        }

        // 2. Check release APK if it exists
        val apkFile = file("${layout.buildDirectory.get()}/outputs/apk/release/app-release.apk")
        if (apkFile.exists()) {
            val zip = java.util.zip.ZipFile(apkFile)
            val entries = zip.entries().asSequence().map { it.name }.toList()
            if (entries.any { it.contains("NotificationRecorder") }) {
                zip.close()
                throw GradleException("Invariant violation: NotificationRecorder file found in release APK zip entries!")
            }

            val dexEntries = entries.filter { it.endsWith(".dex") }
            val forbiddenClassDescriptor = "Lcom/duarf/capture/debug/NotificationRecorder;"
            for (dexName in dexEntries) {
                val input = zip.getInputStream(zip.getEntry(dexName))
                val bytes = input.readBytes()
                input.close()
                val content = String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                if (content.contains(forbiddenClassDescriptor)) {
                    zip.close()
                    throw GradleException("Invariant violation: NotificationRecorder class found in release APK dex: $dexName!")
                }
            }
            zip.close()
        }
    }
}

val verifyDangerQualifyingSignals by tasks.registering {
    description = "Enforces that DANGER_QUALIFYING_SIGNALS matches between docs/ARCHITECTURE.md and packs/rules.json"
    group = "verification"
    doLast {
        val expected = setOf("L01", "L02", "L03", "L07", "L09", "L10", "L11", "A01", "A02", "A04")

        // 1. Verify packs/rules.json
        val rulesFile = rootProject.file("packs/rules.json")
        if (!rulesFile.exists()) {
            throw GradleException("packs/rules.json does not exist!")
        }
        val rulesText = rulesFile.readText()
        val jsonPattern = Regex(""""danger_qualifying_signals"\s*:\s*\[([^\]]+)\]""")
        val matchRules = jsonPattern.find(rulesText)
            ?: throw GradleException("Invariant 6 violation: 'danger_qualifying_signals' not found in packs/rules.json")
        val rulesSignals = matchRules.groupValues[1]
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .toSet()
        if (rulesSignals != expected) {
            throw GradleException("Invariant 6 violation: packs/rules.json danger_qualifying_signals ($rulesSignals) does not match expected $expected")
        }

        // 2. Verify docs/ARCHITECTURE.md
        val archFile = rootProject.file("docs/ARCHITECTURE.md")
        if (!archFile.exists()) {
            throw GradleException("docs/ARCHITECTURE.md does not exist!")
        }
        val archText = archFile.readText()
        val docPattern = Regex("""Danger qualifying signals[^\n:]*:\s*([A-Za-z0-9,\s]+)""")
        val matchDoc = docPattern.find(archText)
            ?: throw GradleException("Invariant 6 violation: 'Danger qualifying signals' not found in docs/ARCHITECTURE.md")
        val docSignals = matchDoc.groupValues[1]
            .split(",")
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .toSet()
        if (docSignals != expected) {
            throw GradleException("Invariant 6 violation: docs/ARCHITECTURE.md Danger qualifying signals ($docSignals) does not match expected $expected")
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDependencies, verifyPermissions, verifyExportedComponents, verifyNoContentLogging, verifyNoDebugToolsInRelease, verifyDangerQualifyingSignals)
}
