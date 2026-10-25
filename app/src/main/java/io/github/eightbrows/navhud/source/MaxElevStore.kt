package io.github.eightbrows.navhud.source

import android.content.Context
import io.github.eightbrows.navhud.core.io.MaxElevFile

/**
 * 最高標高のファイル（assets/maxelev.bin。tools/build_maxelev.py で作る。20261025-D07）を読む。
 * 起動のあとに一度だけファイル全体（約 4.7 MB）を読んで持っておく（展開は検索のときに 1 次メッシュ 1 つずつ）。
 * 読めなければ null（地上高を出さずに動く）。IO のスレッドで呼ぶ。
 */
object MaxElevStore {
    const val ASSET = "maxelev.bin"

    fun load(context: Context): MaxElevFile? =
        runCatching { context.assets.open(ASSET).use { MaxElevFile.decode(it.readBytes()) } }.getOrNull()
}
