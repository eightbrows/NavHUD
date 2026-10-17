package io.github.eightbrows.navhud.source

import android.content.Context
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.BaseMapFile

/**
 * 背景地図（§8）のファイル（assets/basemap.bin。tools/gm2basemap.ps1 で作る）を読む。
 * 小さい（約 300 KB）ので、起動のあとに一度だけ全部読んで持っておく。読めなければ空（背景地図なしで動く）。
 */
object BaseMapStore {
    const val ASSET = "basemap.bin"

    fun load(context: Context): BaseMap =
        runCatching { context.assets.open(ASSET).use { BaseMapFile.decode(it.readBytes()) } }.getOrDefault(BaseMap.EMPTY)
}
