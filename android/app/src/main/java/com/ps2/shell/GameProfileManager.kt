package com.ps2.shell

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class GameProfile(
    val gameId: String,
    var title: String,
    var folderUri: String,
    var layoutJson: String = "",
    var graphicsBackend: Int = 0
)

object GameProfileManager {

    private const val PREFS_NAME = "ps2_game_profiles"
    private const val KEY_PROFILES = "profiles"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getProfiles(context: Context): List<GameProfile> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val list = mutableListOf<GameProfile>()

        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    GameProfile(
                        gameId = obj.getString("gameId"),
                        title = obj.optString("title", "Game"),
                        folderUri = obj.optString("folderUri", ""),
                        layoutJson = obj.optString("layoutJson", ""),
                        graphicsBackend = obj.optInt("graphicsBackend", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    fun getProfile(context: Context, gameId: String): GameProfile? {
        return getProfiles(context).find { it.gameId == gameId }
    }

    fun saveProfile(context: Context, profile: GameProfile) {
        val profiles = getProfiles(context).toMutableList()
        val index = profiles.indexOfFirst { it.gameId == profile.gameId }

        if (index != -1) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }

        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("gameId", p.gameId)
            obj.put("title", p.title)
            obj.put("folderUri", p.folderUri)
            obj.put("layoutJson", p.layoutJson)
            obj.put("graphicsBackend", p.graphicsBackend)
            array.put(obj)
        }

        getPrefs(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun deleteProfile(context: Context, gameId: String) {
        val profiles = getProfiles(context).filter { it.gameId != gameId }
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("gameId", p.gameId)
            obj.put("title", p.title)
            obj.put("folderUri", p.folderUri)
            obj.put("layoutJson", p.layoutJson)
            obj.put("graphicsBackend", p.graphicsBackend)
            array.put(obj)
        }

        getPrefs(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }
}
