package com.sukot.vpn.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sukot.vpn.model.VpnProfile

/**
 * SharedPreferences-backed persistence (UI process only).
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("sukot_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadProfiles(): MutableList<VpnProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<VpnProfile>>() {}.type
            gson.fromJson<MutableList<VpnProfile>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveProfiles(profiles: List<VpnProfile>) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
    }

    fun loadSelectedId(): String = prefs.getString(KEY_SELECTED, "") ?: ""

    fun saveSelectedId(id: String) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun loadSettings(): SukotSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return SukotSettings()
        return try {
            gson.fromJson(json, SukotSettings::class.java) ?: SukotSettings()
        } catch (e: Exception) {
            SukotSettings()
        }
    }

    fun saveSettings(settings: SukotSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadSubscriptions(): MutableList<String> {
        val json = prefs.getString(KEY_SUBS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSubscriptions(urls: List<String>) {
        prefs.edit().putString(KEY_SUBS, gson.toJson(urls)).apply()
    }

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_SELECTED = "selected_profile_id"
        private const val KEY_SETTINGS = "settings_json"
        private const val KEY_SUBS = "subscriptions_json"
    }
}
