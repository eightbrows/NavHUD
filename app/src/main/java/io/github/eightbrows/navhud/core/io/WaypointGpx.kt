package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Waypoint
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * GPX の WP 読み込み（§7.2）。<wpt lat lon> の <name> <ele> <time>（目標時刻）と <extensions><deadline>（締切時刻）。
 * 読めない <wpt> はスキップして数える。
 */
object WaypointGpx {

    /** 先頭が XML / GPX らしいか（CSV と見分ける）。 */
    fun looksLikeGpx(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).trimStart('﻿', ' ', '\t', '\r', '\n')
        return head.startsWith("<?xml") || head.startsWith("<gpx")
    }

    /** @param zone ISO 8601 の日時を、ローカル時刻に直すタイムゾーン */
    fun parse(bytes: ByteArray, zone: ZoneId): WaypointParseResult {
        val doc = try {
            factory().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            return WaypointParseResult(emptyList(), 0)
        }
        val nodes = doc.getElementsByTagNameNS("*", "wpt")
        val wps = ArrayList<Waypoint>()
        var skipped = 0
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            val lat = e.getAttribute("lat").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
            val lon = e.getAttribute("lon").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
            if (lat == null || lon == null) {
                skipped++
                continue
            }
            val extensions = child(e, "extensions")
            wps += Waypoint(
                name = child(e, "name")?.textContent?.trim().orEmpty().ifEmpty { "WP${wps.size + 1}" },
                lat = lat,
                lon = lon,
                eleM = child(e, "ele")?.textContent?.trim()?.toDoubleOrNull(),
                targetTime = child(e, "time")?.textContent?.let { time(it, zone) },
                deadlineTime = extensions?.let { child(it, "deadline") }?.textContent?.let { time(it, zone) },
            )
        }
        return WaypointParseResult(wps, skipped)
    }

    /** ISO 8601 の日時ならローカル時刻へ。H:mm(:ss) だけならそのまま。 */
    fun time(s: String, zone: ZoneId): LocalTime? {
        val t = s.trim()
        TimeText.parse(t)?.let { return it }
        return try {
            OffsetDateTime.parse(t).atZoneSameInstant(zone).toLocalTime().truncatedTo(ChronoUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
    }

    /** 直下の子要素（名前空間は問わない）。 */
    private fun child(parent: Element, localName: String): Element? {
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == localName) return n
        }
        return null
    }

    private fun factory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // 外部エンティティを読まない（XXE 対策）
        trySet { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        trySet { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        trySet { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        trySet { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        isExpandEntityReferences = false
    }

    private inline fun trySet(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}
