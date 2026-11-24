package com.novabox.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles

    var activeId: String?
        get() = prefs.getString("active", null)
        set(v) { prefs.edit().putString("active", v).apply() }

    val active: Profile? get() = _profiles.value.firstOrNull { it.id == activeId } ?: _profiles.value.firstOrNull()

    private fun load(): List<Profile> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { Profile.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Profile>) {
        _profiles.value = list
        prefs.edit().putString("list", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun upsert(p: Profile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        save(list)
    }

    fun delete(id: String) {
        save(_profiles.value.filterNot { it.id == id })
        if (activeId == id) activeId = _profiles.value.firstOrNull()?.id
    }
}

/** Favourites are stored on the device, per profile, so they work on every portal. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private var profileId: String = ""

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels
    private val _vod = MutableStateFlow<List<VodItem>>(emptyList())
    val vod: StateFlow<List<VodItem>> = _vod

    fun switchProfile(id: String) {
        profileId = id
        _channels.value = readList("ch_$id") { Channel.fromJson(it) }
        _vod.value = readList("vod_$id") { VodItem.fromJson(it) }
    }

    private fun <T> readList(key: String, f: (JSONObject) -> T): List<T> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { f(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun isFavorite(c: Channel) = _channels.value.any { it.id == c.id }
    fun isFavorite(v: VodItem) = _vod.value.any { it.id == v.id && it.kind == v.kind }

    fun toggle(c: Channel) {
        val list = if (isFavorite(c)) _channels.value.filterNot { it.id == c.id } else _channels.value + c
        _channels.value = list
        prefs.edit().putString("ch_$profileId", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun toggle(v: VodItem) {
        val list = if (isFavorite(v)) _vod.value.filterNot { it.id == v.id && it.kind == v.kind } else _vod.value + v
        _vod.value = list
        prefs.edit().putString("vod_$profileId", JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var bufferSeconds: Int
        get() = prefs.getInt("buffer", 3)
        set(v) { prefs.edit().putInt("buffer", v).apply() }
    var lastChannelId: String?
        get() = prefs.getString("lastChannel", null)
        set(v) { prefs.edit().putString("lastChannel", v).apply() }
}
