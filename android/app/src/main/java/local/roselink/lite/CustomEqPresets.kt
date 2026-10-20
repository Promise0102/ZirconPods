package local.roselink.lite

import android.content.Context
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
    fun save(presets: List<EqPreset>): Boolean {
        require(presets.size <= 20)
        val array = JSONArray()
        for (preset in presets) {
            require(preset.name.isNotBlank() && preset.name.length <= 40 && CustomEqProtocol.valid(preset.gains.toIntArray()))
            array.put(JSONObject().put("id", preset.id).put("name", preset.name).put("gains", JSONArray(preset.gains)))
        }
        return prefs.edit().putString("presets", array.toString()).commit()
    }
}
