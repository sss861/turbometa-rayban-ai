package com.tourmeta.app.managers

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Language Manager - App 语言管理器
 * 1:1 port from iOS LanguageManager.swift
 */
enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    SYSTEM("system", "System", "跟随系统"),
    CHINESE("zh-CN", "Chinese", "中文"),
    TRADITIONAL_CHINESE("zh-HK", "Chinese (Traditional)", "繁體中文"),
    ENGLISH("en", "English", "English"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    SPANISH("es", "Spanish", "Español"),
    FRENCH("fr", "French", "Français");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
    }
}

object LanguageManager {
    private const val PREF_NAME = "language_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"

    private var currentLanguage: AppLanguage = AppLanguage.SYSTEM

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedCode = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code
        currentLanguage = AppLanguage.fromCode(savedCode)
        applyLanguage(context)
    }

    fun getCurrentLanguage(): AppLanguage = currentLanguage

    fun setLanguage(context: Context, language: AppLanguage) {
        currentLanguage = language

        // Save preference
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()

        // Apply language
        applyLanguage(context)
    }

    private fun applyLanguage(context: Context) {
        val localeList = when (currentLanguage) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
            AppLanguage.TRADITIONAL_CHINESE -> LocaleListCompat.forLanguageTags("zh-HK")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.JAPANESE -> LocaleListCompat.forLanguageTags("ja")
            AppLanguage.KOREAN -> LocaleListCompat.forLanguageTags("ko")
            AppLanguage.SPANISH -> LocaleListCompat.forLanguageTags("es")
            AppLanguage.FRENCH -> LocaleListCompat.forLanguageTags("fr")
        }

        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Check if current language is Chinese
     */
    fun isChinese(context: Context): Boolean {
        return when (currentLanguage) {
            AppLanguage.CHINESE -> true
            AppLanguage.TRADITIONAL_CHINESE -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.JAPANESE -> false
            AppLanguage.KOREAN -> false
            AppLanguage.SPANISH -> false
            AppLanguage.FRENCH -> false
            AppLanguage.SYSTEM -> {
                val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
                systemLocale.language == "zh"
            }
        }
    }

    /**
     * Get all available languages
     */
    fun getAvailableLanguages(): List<AppLanguage> = AppLanguage.entries
}
