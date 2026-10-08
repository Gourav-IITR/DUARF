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
            // Injected by androidx.core for secure non-exported dynamic broadcast receivers
            // on Android 13+ (API 33+). Ensures only the app itself can send broadcasts to
            // dynamically registered receivers that specify RECEIVER_NOT_EXPORTED.
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

val verifyNoPiiLeakage by tasks.registering {
    description = "Enforces that no Indian mobile numbers, bank account numbers, raw OTP codes, UPI IDs, or private email addresses are committed in the repository (§2, §14, §19)"
    group = "verification"
    doLast {
        val phoneRegex = Regex("""(?<![\d.a-zA-Z])(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?![\d.a-zA-Z])""")
        val acctRegex = Regex("""(?i)(?:\b(?:a/c|account|ac\s*no|acc\s*no)\b|खाता(?:\s*(?:संख्या|नंबर|नं))?)\s*[:#.-]?\s*\d{9,18}\b""")
        val otpRegex = Regex("""(?i)(?:\botp\b|ओटीपी)\s*[:=is-]{0,15}\s*\b\d{4,8}\b|\b\d{4,8}\b\s*[:=is-]{0,15}\s*(?:\botp\b|ओटीपी)""")
        val upiRegex = Regex("""\b[a-zA-Z0-9.\-_]{2,50}@([a-zA-Z0-9]+)\b""")
        val emailRegex = Regex("""\b[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})\b""")

        val allowedPhones = setOf(
            "+919876543210", "+91 98765 43210", "9876543210", "98765 43210", "+91-98765-43210",
            "+919123456789", "+91 91234 56789", "9123456789", "91234 56789",
            "+919000000000", "+91 90000 00000", "9000000000", "90000 00000",
            "+919999999999", "+91 99999 99999", "9999999999", "99999 99999",
            "+919876543211", "+91 98765 43211", "9876543211", "98765 43211",
            "+447911123456", "+44 7911 123456", "+44 7911123456", "7911123456",
            "18002583838"
        )

        val allowedOtps = setOf(
            "123456", "654321", "000000", "111111",
            "492019", "382910", "592014", "392018"
        )

        val allowedUpi = setOf(
            "user@okaxis",
            "helpme2024@ybl"
        )

        val allowedEmails = setOf(
            "user@example.com",
            "support@example.com",
            "test@example.com"
        )

        val handlesFile = rootProject.file("packs/lists/upi_handles.txt")
        val upiHandles = if (handlesFile.exists()) {
            handlesFile.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        } else {
            setOf("okaxis", "okhdfcbank", "oksbi", "okicici", "ybl", "ibl", "axl", "paytm", "apl", "upi", "postbank", "kotak", "barodampay", "aubank", "indus", "federal", "jupiteraxis", "fbl")
        }

        fun isAllowedEmail(raw: String, domain: String): Boolean {
            val rawLower = raw.lowercase()
            val domLower = domain.lowercase()
            if (rawLower in allowedEmails) return true
            if (domLower == "example.com" || domLower.endsWith(".example.com")) return true
            if (domLower == "example.org" || domLower.endsWith(".example.org")) return true
            if (domLower == "example.net" || domLower.endsWith(".example.net")) return true
            if (domLower == "example" || domLower.endsWith(".example")) return true
            if (domLower == "test" || domLower.endsWith(".test")) return true
            if (domLower == "invalid" || domLower.endsWith(".invalid")) return true
            if (domLower == "localhost" || domLower.endsWith(".localhost")) return true
            return false
        }

        val tollFreeRegex = Regex("""^(?:\+91[\s-]?)?1800\d{6,7}$""")
        val digitsExtractRegex = Regex("""\b\d{4,8}\b""")

        // Obtain tracked files from git, with fallback to directory walk
        val trackedFiles: List<File> = try {
            val byteOut = java.io.ByteArrayOutputStream()
            project.exec {
                commandLine("git", "ls-files")
                standardOutput = byteOut
            }
            byteOut.toString(java.nio.charset.StandardCharsets.UTF_8)
                .lines()
                .filter { it.isNotBlank() }
                .map { rootProject.file(it) }
                .filter { it.exists() && it.isFile }
        } catch (e: Exception) {
            val ignoredDirs = setOf(".git", "build", ".gradle", ".idea", "captures", ".cxx", ".externalNativeBuild", "venv", ".venv")
            rootProject.rootDir.walkTopDown()
                .filter { file -> !ignoredDirs.any { file.path.contains("/$it/") || file.path.endsWith("/$it") } }
                .filter { it.isFile }
                .toList()
        }

        val ignoredExtensions = setOf("bin", "apk", "aab", "jar", "png", "jpg", "jpeg", "ico", "webp", "class", "dex")
        val violations = mutableListOf<String>()

        trackedFiles.forEach { file ->
            if (file.extension.lowercase() !in ignoredExtensions) {
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        phoneRegex.findAll(line).forEach { match ->
                            val raw = match.value.trim()
                            val norm = raw.replace(" ", "").replace("-", "")
                            if (raw !in allowedPhones && norm !in allowedPhones && !tollFreeRegex.matches(raw)) {
                                violations.add("PHONE: ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '$raw' in line: ${line.take(100)}")
                            }
                        }

                        acctRegex.findAll(line).forEach { match ->
                            violations.add("ACCOUNT: ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '${match.value.trim()}' in line: ${line.take(100)}")
                        }

                        otpRegex.findAll(line).forEach { match ->
                            val raw = match.value.trim()
                            val digitsMatch = digitsExtractRegex.find(raw)
                            val digits = digitsMatch?.value
                            if (digits != null && digits !in allowedOtps) {
                                violations.add("OTP: ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '$raw' in line: ${line.take(100)}")
                            }
                        }

                        upiRegex.findAll(line).forEach { match ->
                            val handle = match.groupValues[1].lowercase()
                            if (handle in upiHandles) {
                                val raw = match.value.lowercase()
                                if (raw !in allowedUpi) {
                                    violations.add("UPI: ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '${match.value}' in line: ${line.take(100)}")
                                }
                            }
                        }

                        emailRegex.findAll(line).forEach { match ->
                            val raw = match.value
                            val domain = match.groupValues[1]
                            if (!isAllowedEmail(raw, domain)) {
                                violations.add("EMAIL: ${file.relativeTo(rootProject.rootDir)}:${index + 1}: '$raw' in line: ${line.take(100)}")
                            }
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "CRITICAL Invariant 2 violation (§2, §14, §19): Potential PII leakage detected in tracked repository files:\n" +
                violations.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDependencies, verifyPermissions, verifyExportedComponents, verifyNoContentLogging, verifyNoDebugToolsInRelease, verifyDangerQualifyingSignals, verifyNoPiiLeakage)
}

