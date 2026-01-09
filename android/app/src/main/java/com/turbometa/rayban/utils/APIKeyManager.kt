package com.tourmeta.app.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tourmeta.app.managers.AlibabaEndpoint
import com.tourmeta.app.managers.APIProvider
import com.tourmeta.app.managers.APIProviderManager

/**
 * API Key Manager
 * Secure storage and retrieval of API keys using EncryptedSharedPreferences
 * Supports multiple API providers (Alibaba Dashscope Beijing/Singapore, OpenRouter, Google)
 * 1:1 port from iOS APIKeyManager.swift
 */
class APIKeyManager(context: Context) {

    companion object {
        private const val TAG = "APIKeyManager"
        private const val PREFS_NAME = "turbometa_secure_prefs"

        // Account names for different providers
        private const val KEY_ALIBABA_BEIJING = "alibaba-beijing-api-key"
        private const val KEY_ALIBABA_SINGAPORE = "alibaba-singapore-api-key"
        private const val KEY_OPENROUTER = "openrouter-api-key"
        private const val KEY_GOOGLE = "google-api-key"
        private const val KEY_LEGACY = "qwen_api_key" // For backward compatibility

        // Settings keys
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_OUTPUT_LANGUAGE = "output_language"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_VISITOR_AGE = "visitor_age"
        private const val KEY_VISITOR_AGE_GROUP = "visitor_age_group"
        private const val KEY_GUIDE_STYLE = "guide_style"
        private const val KEY_BLIND_MODE = "blind_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        @Volatile
        private var instance: APIKeyManager? = null

        fun getInstance(context: Context): APIKeyManager {
            return instance ?: synchronized(this) {
                instance ?: APIKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        migrateLegacyKey()
    }

    // MARK: - Migration

    private fun migrateLegacyKey() {
        try {
            // Migrate old qwen key to new Alibaba Beijing format
            val legacyKey = sharedPreferences.getString(KEY_LEGACY, null)
            if (!legacyKey.isNullOrBlank() && sharedPreferences.getString(KEY_ALIBABA_BEIJING, null).isNullOrBlank()) {
                sharedPreferences.edit()
                    .putString(KEY_ALIBABA_BEIJING, legacyKey)
                    .remove(KEY_LEGACY)
                    .apply()
                Log.i(TAG, "Migrated legacy qwen API key to Alibaba Beijing")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration error: ${e.message}")
        }
    }

    // MARK: - Provider-specific API Key Management

    fun saveAPIKey(key: String, provider: APIProvider, endpoint: AlibabaEndpoint? = null): Boolean {
        return try {
            if (key.isBlank()) return false
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.edit().putString(accountKey, key).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key: ${e.message}")
            false
        }
    }

    fun getAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): String? {
        return try {
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.getString(accountKey, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key: ${e.message}")
            null
        }
    }

    fun deleteAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): Boolean {
        return try {
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.edit().remove(accountKey).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete API key: ${e.message}")
            false
        }
    }

    fun hasAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): Boolean {
        return !getAPIKey(provider, endpoint).isNullOrBlank()
    }

    // MARK: - Google API Key (for Live AI)

    fun saveGoogleAPIKey(key: String): Boolean {
        return try {
            if (key.isBlank()) return false
            sharedPreferences.edit().putString(KEY_GOOGLE, key).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Google API key: ${e.message}")
            false
        }
    }

    fun getGoogleAPIKey(): String? {
        return try {
            sharedPreferences.getString(KEY_GOOGLE, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Google API key: ${e.message}")
            null
        }
    }

    fun deleteGoogleAPIKey(): Boolean {
        return try {
            sharedPreferences.edit().remove(KEY_GOOGLE).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Google API key: ${e.message}")
            false
        }
    }

    fun hasGoogleAPIKey(): Boolean {
        return !getGoogleAPIKey().isNullOrBlank()
    }

    // MARK: - Backward Compatible Methods (defaults to current provider)

    fun saveAPIKey(key: String): Boolean {
        return saveAPIKey(key, APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun getAPIKey(): String? {
        return getAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun deleteAPIKey(): Boolean {
        return deleteAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun hasAPIKey(): Boolean {
        return hasAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    // MARK: - Private Helpers

    private fun accountName(provider: APIProvider, endpoint: AlibabaEndpoint?): String {
        return when (provider) {
            APIProvider.ALIBABA -> {
                val effectiveEndpoint = endpoint ?: APIProviderManager.staticAlibabaEndpoint
                when (effectiveEndpoint) {
                    AlibabaEndpoint.BEIJING -> KEY_ALIBABA_BEIJING
                    AlibabaEndpoint.SINGAPORE -> KEY_ALIBABA_SINGAPORE
                }
            }
            APIProvider.OPENROUTER -> KEY_OPENROUTER
        }
    }

    // MARK: - Settings (non-sensitive data)

    // AI Model
    fun saveAIModel(model: String) {
        sharedPreferences.edit().putString(KEY_AI_MODEL, model).apply()
    }

    fun getAIModel(): String {
        return sharedPreferences.getString(KEY_AI_MODEL, "qwen3-omni-flash-realtime") ?: "qwen3-omni-flash-realtime"
    }

    // Output Language
    fun saveOutputLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_OUTPUT_LANGUAGE, language).apply()
    }

    fun getOutputLanguage(): String {
        return sharedPreferences.getString(KEY_OUTPUT_LANGUAGE, "zh-CN") ?: "zh-CN"
    }

    // Video Quality
    fun saveVideoQuality(quality: String) {
        sharedPreferences.edit().putString(KEY_VIDEO_QUALITY, quality).apply()
    }

    fun getVideoQuality(): String {
        return sharedPreferences.getString(KEY_VIDEO_QUALITY, "MEDIUM") ?: "MEDIUM"
    }

    // RTMP URL
    fun saveRtmpUrl(url: String) {
        sharedPreferences.edit().putString(KEY_RTMP_URL, url).apply()
    }

    fun getRtmpUrl(): String? {
        return sharedPreferences.getString(KEY_RTMP_URL, null)
    }

    fun saveVisitorAge(age: Int) {
        sharedPreferences.edit().putInt(KEY_VISITOR_AGE, age).apply()
    }

    fun getVisitorAge(): Int {
        return sharedPreferences.getInt(KEY_VISITOR_AGE, 30)
    }

    fun saveVisitorAgeGroup(group: AgeGroup) {
        sharedPreferences.edit().putString(KEY_VISITOR_AGE_GROUP, group.name).apply()
    }

    fun getVisitorAgeGroup(): AgeGroup {
        val v = sharedPreferences.getString(KEY_VISITOR_AGE_GROUP, AgeGroup.ADULT_18_30.name) ?: AgeGroup.ADULT_18_30.name
        return AgeGroup.valueOf(v)
    }

    fun saveGuideStyle(style: GuideStyle) {
        sharedPreferences.edit().putString(KEY_GUIDE_STYLE, style.name).apply()
    }

    fun getGuideStyle(): GuideStyle {
        val v = sharedPreferences.getString(KEY_GUIDE_STYLE, GuideStyle.STORYTELLING.name) ?: GuideStyle.STORYTELLING.name
        return GuideStyle.valueOf(v)
    }

    fun saveBlindMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BLIND_MODE, enabled).apply()
    }

    fun isBlindMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_BLIND_MODE, false)
    }

    fun setOnboardingDone(done: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun isOnboardingDone(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_DONE, false)
    }
}

// Available AI models for Live AI
enum class AIModel(val id: String, val displayName: String) {
    // Alibaba Qwen Omni
    QWEN_FLASH_REALTIME("qwen3-omni-flash-realtime", "Qwen3 Omni Flash (Realtime)"),
    QWEN_STANDARD_REALTIME("qwen3-omni-standard-realtime", "Qwen3 Omni Standard (Realtime)"),
    // Google Gemini
    GEMINI_FLASH("gemini-2.0-flash-exp", "Gemini 2.0 Flash")
}

// Available output languages
enum class OutputLanguage(val code: String, val displayName: String, val nativeName: String) {
    CHINESE("zh-CN", "Chinese", "\u4e2d\u6587"),
    TRADITIONAL_CHINESE("zh-HK", "Chinese (Traditional)", "\u7e41\u9ad4\u4e2d\u6587"),
    ENGLISH("en-US", "English", "English"),
    JAPANESE("ja-JP", "Japanese", "\u65e5\u672c\u8a9e"),
    KOREAN("ko-KR", "Korean", "\ud55c\uad6d\uc5b4"),
    SPANISH("es-ES", "Spanish", "Espa\u00f1ol"),
    FRENCH("fr-FR", "French", "Fran\u00e7ais")
}

// Video quality options
enum class StreamQuality(val id: String, val displayNameResId: Int, val descriptionResId: Int) {
    LOW("LOW", com.tourmeta.app.R.string.quality_low, com.tourmeta.app.R.string.quality_low_desc),
    MEDIUM("MEDIUM", com.tourmeta.app.R.string.quality_medium, com.tourmeta.app.R.string.quality_medium_desc),
    HIGH("HIGH", com.tourmeta.app.R.string.quality_high, com.tourmeta.app.R.string.quality_high_desc)
}

enum class GuideStyle(val id: String, val displayNameResId: Int) {
    CONCISE("concise", com.tourmeta.app.R.string.guide_style_concise),
    STORYTELLING("story", com.tourmeta.app.R.string.guide_style_story),
    ACADEMIC("academic", com.tourmeta.app.R.string.guide_style_academic);
    fun getDisplayName(context: Context): String = context.getString(displayNameResId)
}

enum class AgeGroup(val displayNameResId: Int) {
    CHILD_UNDER_12(com.tourmeta.app.R.string.age_child_under_12),
    TEEN_12_18(com.tourmeta.app.R.string.age_teen_12_18),
    ADULT_18_30(com.tourmeta.app.R.string.age_adult_18_30),
    ADULT_30_50(com.tourmeta.app.R.string.age_adult_30_50),
    SENIOR_OVER_50(com.tourmeta.app.R.string.age_senior_over_50);
    fun getDisplayName(context: Context): String = context.getString(displayNameResId)
}
