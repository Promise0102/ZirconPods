package local.roselink.lite

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

internal data class EqPreset(val id: String, val name: String, val gains: List<Int>)

/** Phone-local user presets. Saving never sends a command to the earphones. */
internal class CustomEqPresets(context: Context) {
    private val prefs = context.getSharedPreferences("custom_eq_presets", Context.MODE_PRIVATE)
    fun load(): List<EqPreset> {
        val raw = prefs.getString("presets", null) ?: return listOf(
            EqPreset("reference-bass", "土嗨（不盖人声）", listOf(2, 3, 1, 0, 1, 0, 0, 0, 1, 0)),
            EqPreset("reference-vocal", "极致人声", listOf(0, -1, 2, 3, 4, 2, 0, -1, 0, 1))
        )
        return try {
            val array = JSONArray(raw)
            val seen = mutableSetOf<String>()
            buildList {
                for (i in 0 until minOf(array.length(), 20)) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").take(80)
                    val name = item.optString("name").trim().take(40)
                    val bands = item.optJSONArray("gains") ?: continue
                    if (bands.length() != 10) continue
                    val gains = (0 until 10).map { bands.optInt(it, 99) }
                    if (id.isNotEmpty() && name.isNotEmpty() && CustomEqProtocol.valid(gains.toIntArray()) && seen.add(id))
                        add(EqPreset(id, name, gains))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
    fun favorites(): Set<String> = prefs.getStringSet(FAVORITES, emptySet()).orEmpty().toSet()
    fun favoriteOnly(): Boolean = prefs.getBoolean("favorites_only", false)
    fun setFavoriteOnly(only: Boolean): Boolean = prefs.edit().putBoolean("favorites_only", only).commit()
    fun setFavorite(key: String, favorite: Boolean): Boolean {
        require(key.startsWith("local:") || key.startsWith("style:"))
        val keys = favorites().toMutableSet().apply { if (favorite) add(key) else remove(key) }
        return prefs.edit().putStringSet(FAVORITES, keys).commit()
    }
    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
    fun save(presets: List<EqPreset>, favoriteKeys: Set<String>? = null): Boolean {
        require(presets.size <= 20)
        val array = JSONArray()
        for (preset in presets) {
            require(preset.name.isNotBlank() && preset.name.length <= 40 && CustomEqProtocol.valid(preset.gains.toIntArray()))
            array.put(JSONObject().put("id", preset.id).put("name", preset.name).put("gains", JSONArray(preset.gains)))
        }
        val localKeys = presets.map { "local:${it.id}" }.toSet()
        val nextFavorites = (favoriteKeys ?: favorites()).filter { !it.startsWith("local:") || it in localKeys }.toSet()
        return prefs.edit().putString("presets", array.toString()).putStringSet(FAVORITES, nextFavorites).commit()
    }
    companion object { const val FAVORITES = "favorite_eq_ids" }
}

/** Observe by stable preset identity, so rename/edit, folds and multiple app windows stay consistent. */
@Composable
internal fun rememberEqFavorites(store: CustomEqPresets): Set<String> {
    var keys by remember(store) { mutableStateOf(store.favorites()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == CustomEqPresets.FAVORITES) keys = store.favorites()
        }
        store.observe(listener)
        onDispose { store.unobserve(listener) }
    }
    return keys
}

internal data class EqLibraryPreset(val key: String, val name: String, val gains: List<Int>,
    val localId: String? = null, val styleIndex: Int = -1)

internal fun eqLibraryPresets(local: List<EqPreset>, styles: List<EqStyleEntry>): List<EqLibraryPreset> =
    local.map { EqLibraryPreset("local:${it.id}", it.name, it.gains, localId = it.id) } +
    styles.mapIndexed { index, style -> EqLibraryPreset(style.key, "风格 · ${style.curve.name}", style.curve.gains.toList(), styleIndex = index) }

internal fun orderedEqPresets(presets: List<EqLibraryPreset>, favorites: Set<String>, onlyFavorites: Boolean = false): List<EqLibraryPreset> =
    presets.filter { !onlyFavorites || it.key in favorites }.sortedByDescending { it.key in favorites }
