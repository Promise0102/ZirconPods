package local.roselink.lite

import android.content.Context

internal object EqStyleCatalog {
    fun load(context: Context): List<GraphicEqFile.Curve> = context.assets.list("eq_styles").orEmpty()
        .filter { it.endsWith(".txt") }.sorted().map { file ->
            val text = context.assets.open("eq_styles/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
            GraphicEqFile.parse(text, file.removeSuffix(".txt"))
        }
}
