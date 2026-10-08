package com.duarf.app.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.duarf.engine.normalize.LanguageScriptDetector
import java.util.Locale

/** A language the app's own screens and notifications can be shown in. */
data class UiLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String
) {
    val isBeta: Boolean get() = LanguageScriptDetector.isBetaLanguage(tag)
}

/**
 * The app's UI language. English unless the user picks another one; it never follows the
 * phone's language on its own.
 *
 * This only changes which strings.xml the app reads. Detection is unaffected: the engine
 * loads every language pack and never sees this setting (see UiLocaleIndependenceTest).
 *
 * Android 13+ stores the choice as the system per-app language, so it also shows up in
 * Settings > Apps > DUARF > Language. Android 8–12 have no per-app language, so the choice
 * lives in a small SharedPreferences file and contexts are wrapped with [wrap].
 */
object AppLanguage {

    const val DEFAULT_TAG = "en"

    private const val PREFS_NAME = "duarf_ui_language"
    private const val KEY_TAG = "tag"

    /** Every locale with a values-* folder, English first. */
    val SUPPORTED: List<UiLanguage> = listOf(
        UiLanguage("en", "English", "English"),
        UiLanguage("hi", "हिन्दी", "Hindi"),
        UiLanguage("hi-Latn", "Hinglish", "Hindi in English letters"),
        UiLanguage("bn", "বাংলা", "Bengali"),
        UiLanguage("mr", "मराठी", "Marathi"),
        UiLanguage("te", "తెలుగు", "Telugu"),
        UiLanguage("ta", "தமிழ்", "Tamil"),
        UiLanguage("gu", "ગુજરાતી", "Gujarati"),
        UiLanguage("kn", "ಕನ್ನಡ", "Kannada"),
        UiLanguage("ml", "മലയാളം", "Malayalam"),
        UiLanguage("or", "ଓଡ଼ିଆ", "Odia"),
        UiLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi")
    )

    fun find(tag: String): UiLanguage = SUPPORTED.firstOrNull { it.tag == tag } ?: SUPPORTED.first()

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return normalize(locales[0].toLanguageTag())
        }
        return prefs(context).getString(KEY_TAG, null) ?: DEFAULT_TAG
    }

    /**
     * Saves and applies [tag]. On Android 13+ the system recreates the activity itself;
     * on older versions the activity is recreated here so the new strings load.
     */
    fun set(context: Context, tag: String) {
        val normalized = normalize(tag)
        prefs(context).edit().putString(KEY_TAG, normalized).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(normalized)
        } else {
            context.findActivity()?.recreate()
        }
    }

    /** First launch on Android 13+: pin English so the app does not follow the phone's language. */
    fun ensureDefault(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(current(context))
            }
        }
    }

    /** Android 8–12: returns [base] with the chosen language applied. A no-op on 13+. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(current(base))))
        return base.createConfigurationContext(config)
    }

    /** Maps any BCP 47 tag ("hi-IN", "hi-Latn-IN", "en-US") onto a supported tag, else English. */
    fun normalize(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val withScript = if (locale.script.isNotEmpty()) "${locale.language}-${locale.script}" else locale.language
        return SUPPORTED.firstOrNull { it.tag == withScript }?.tag
            ?: SUPPORTED.firstOrNull { it.tag == locale.language }?.tag
            ?: DEFAULT_TAG
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
