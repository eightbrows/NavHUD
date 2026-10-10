package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.io.ZipSession
import io.github.eightbrows.navhud.source.ZipSessions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * GpsLogger の zip の中のセッションの一覧（§6.7）。1つ選んで再生する（セッションが1つだけでも出す）。
 * 1行に 開始時刻（端末の時刻帯）、長さ（最後の点 − 最初の点）と点の数、セッションの名前。読める行のないセッションは選べない。
 */
@Composable
fun ZipSessionDialog(zip: ZipSessions, zone: ZoneId, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zip_sessions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zip.displayName, style = RowNote)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(zip.sessions, key = { it.entryName }) { s -> SessionRow(s, zone) { onPick(s.entryName) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SessionRow(s: ZipSession, zone: ZoneId, onClick: () -> Unit) {
    val start = s.summary.startMs
    val end = s.summary.endMs
    val readable = start != null && end != null && s.summary.points > 0
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HudColors.ScaleDim, RoundedCornerShape(6.dp))
            .clickable(enabled = readable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (start != null && end != null && readable) {
            Text(START_FORMAT.format(Instant.ofEpochMilli(start).atZone(zone)), style = RowLabel)
            Text(
                stringResource(R.string.zip_session_detail, duration(end - start), s.summary.points.toString()),
                style = ValueText,
            )
        } else {
            Text(stringResource(R.string.zip_session_empty), style = RowLabel.copy(color = HudColors.WpReached))
        }
        if (s.name.isNotEmpty()) Text(s.name, style = RowNote)
    }
}

private val RowLabel get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ValueText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)
private val RowNote get() = TextStyle(fontSize = 12.sp, color = HudColors.ScaleDim)

// 数字・日付・時刻の書き方は、端末の言語によらず同じ（§6.11）
private val START_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

/** 長さ [ms] を H:MM:SS に */
private fun duration(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d:%02d".format(Locale.US, sec / 3600, sec / 60 % 60, sec % 60)
}
