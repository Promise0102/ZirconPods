package local.roselink.lite

import android.content.Context

internal data class EqStyleEntry(val key: String, val curve: GraphicEqFile.Curve)

internal object EqStyleCatalog {
    fun load(context: Context): List<GraphicEqFile.Curve> = entries(context).map { it.curve }
    fun entries(context: Context): List<EqStyleEntry> = context.assets.list("eq_styles").orEmpty()
        .filter { it.endsWith(".txt") }.sorted().map { file ->
            val text = context.assets.open("eq_styles/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
            EqStyleEntry("style:$file", GraphicEqFile.parse(text, file.removeSuffix(".txt")))
        }
}
