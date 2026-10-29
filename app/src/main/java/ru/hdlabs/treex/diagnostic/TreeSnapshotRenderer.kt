package ru.hdlabs.treex.diagnostic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot

/** Simple text snapshot for debug uploads (layout is rendered by Topola in the app). */
object TreeSnapshotRenderer {
    private const val MAX_EDGE_PX = 2048

    fun renderPng(
        context: Context,
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink>,
        homePersonId: String?,
    ): ByteArray {
        val density = context.resources.displayMetrics.density.coerceIn(1.5f, 4f)
        val lines = buildList {
            add("TreeX snapshot (Topola viewer in app)")
            add("people=${people.size} unions=${unions.size} openLinks=${openLinks.size}")
            if (!homePersonId.isNullOrBlank()) add("home=$homePersonId")
            add("")
            people.sortedBy { it.lastName + it.firstName }.forEach { person ->
                val tag = when {
                    person.isVacantSlot() -> " [vacant]"
                    person.id == homePersonId -> " [home]"
                    else -> ""
                }
                add("${person.id}: ${person.fullName()}$tag")
            }
        }
        val text = lines.joinToString("\n")
        val bitmap = renderTextBitmap(text, density)
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            out.toByteArray()
        }
    }

    private fun renderTextBitmap(text: String, density: Float): Bitmap {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
        }
        val widthPx = (320f * density).toInt().coerceAtMost(MAX_EDGE_PX)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val heightPx = layout.height.coerceAtMost(MAX_EDGE_PX)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.parseColor("#0B1220"))
            layout.draw(this)
        }
        return bitmap
    }
}
