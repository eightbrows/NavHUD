package io.github.eightbrows.navhud.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/**
 * 言語のリソースから組み立てる文字（ViewModel のメッセージなど）。画面に出すときに、その時の言語で文字にする。
 * 引数は文字にしたもの（数値は Locale.US で文字にしてから渡す）か、入れ子の UiText。
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(res: Resources): String =
        res.getString(id, *args.map { if (it is UiText) it.resolve(res) else it.toString() }.toTypedArray())
}

fun uiText(@StringRes id: Int, vararg args: Any) = UiText(id, args.toList())

@Composable
fun UiText.asString(): String = resolve(LocalResources.current)
