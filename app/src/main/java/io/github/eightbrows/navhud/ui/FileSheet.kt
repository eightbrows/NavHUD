package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.view.LoadSummary
import java.time.ZoneId

/** ファイルの画面の「読み込み中」: ファイルの名前と、中身のひとこと（1 行ずつ） */
data class SheetCurrentFile(val name: String, val lines: List<String>)

private val Title get() = TextStyle(fontSize = 18.sp, color = HudColors.Scale)
private val Heading get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.ScaleDim)
private val Name get() = TextStyle(fontSize = 15.sp, color = HudColors.Scale)
private val Small get() = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = HudColors.ScaleDim)

/**
 * ファイルの画面（20261104-D03）: 軌跡（上部バーの FILE）と WP リスト（WP設定のインポート）で同じ形の、下からせり上がる面。
 * 上から a. 見出し（● ＋ 軌跡 / WP リスト、右に閉じる）、b. 読み込み中（ファイルの名前・ひとこと、右にクリア。なければ「なし」）、
 * c. 最近のファイル（新しい順に 5 件まで。押すと開く。右の ✕ で履歴から消す。開けないものはグレーで「開けません」、押せない）、
 * d. 「ファイルを選ぶ…」（今までのファイル選び）。
 * dot は ● の色（地図に描く色: 軌跡は「読み込んだ軌跡の色」、WP はマゼンタ）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSheet(
    sheet: FileSheetState,
    dot: Color,
    current: SheetCurrentFile?,
    zone: ZoneId,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HudColors.Background,
        contentColor = HudColors.Scale,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).background(HudColors.WpDisabled, RoundedCornerShape(2.dp))) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // a. 見出し
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).background(dot, CircleShape))
                Text(
                    stringResource(if (sheet.kind == FileSheetKind.TRACK) R.string.sheet_track else R.string.sheet_wp),
                    style = Title, modifier = Modifier.weight(1f),
                )
                HudButton(stringResource(R.string.close), onDismiss, small = true)
            }

            // b. 読み込み中
            Text(stringResource(R.string.sheet_loaded), style = Heading)
            if (current == null) {
                Text(stringResource(R.string.sheet_none), style = Name.copy(color = HudColors.ScaleDim))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(current.name, style = Name)
                        current.lines.forEach { Text(it, style = Small) }
                    }
                    HudButton(stringResource(R.string.sheet_clear), onClear, small = true)
                }
            }

            // c. 最近のファイル
            Text(stringResource(R.string.sheet_recent), style = Heading)
            if (sheet.recent.isEmpty()) {
                Text(stringResource(R.string.sheet_none), style = Name.copy(color = HudColors.ScaleDim))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in sheet.recent) RecentRow(r, zone, { onOpen(r.uri) }, { onRemove(r.uri) })
                }
            }

            // d. ファイルを選ぶ…
            Box(Modifier.padding(top = 4.dp)) { HudButton(stringResource(R.string.sheet_choose), onChoose, fill = true) }
        }
    }
}

/** 最近のファイルの 1 行: 名前と、最後に開いた日時（小さく）。開けないものはグレーで「開けません」（押せない。✕ では消せる） */
@Composable
private fun RecentRow(r: RecentFileRow, zone: ZoneId, onOpen: () -> Unit, onRemove: () -> Unit) {
    val ok = r.openable != false
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (ok) HudColors.WpDisabled else HudColors.WpReached, shape)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = ok, onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                r.name, style = Name.copy(color = if (ok) HudColors.Scale else HudColors.WpReached),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(LoadSummary.openedAt(r.openedAtMs, zone), style = Small.copy(color = if (ok) HudColors.ScaleDim else HudColors.WpReached))
                if (!ok) Text(stringResource(R.string.sheet_cannot_open), style = Small.copy(color = HudColors.WpDisabled))
            }
        }
        val desc = stringResource(R.string.sheet_remove)
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = Name.copy(color = HudColors.ScaleDim))
        }
    }
}
