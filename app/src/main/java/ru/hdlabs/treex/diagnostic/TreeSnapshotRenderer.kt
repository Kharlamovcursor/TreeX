package ru.hdlabs.treex.diagnostic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.LinkSide
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.TreeLinkGeometry
import ru.hdlabs.treex.domain.TreePlacement
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot
import ru.hdlabs.treex.domain.yearsLabel
import kotlin.math.max
import kotlin.math.min

/** Static bitmap of the native [TreeLayout] (not relatives-tree WebView) for debug uploads. */
object TreeSnapshotRenderer {
    private const val MAX_EDGE_PX = 4096

    fun renderPng(
        context: Context,
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink>,
        homePersonId: String?,
    ): ByteArray {
        val density = context.resources.displayMetrics.density.coerceIn(1.5f, 4f)
        val placement = TreeLayout.place(
            people = people,
            unions = unions,
            openLinks = openLinks,
            homePersonId = homePersonId?.takeIf { it.isNotBlank() },
        )
        val bitmap = renderBitmap(placement, people.associateBy { it.id }, density)
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            out.toByteArray()
        }
    }

    internal fun renderBitmap(
        placement: TreePlacement,
        byId: Map<String, PersonEntity>,
        density: Float,
    ): Bitmap {
        fun dp(v: Float): Float = v * density
        val contentW = dp(placement.width.coerceAtLeast(TreeLayout.CARD_W + 32f))
        val contentH = dp(placement.height.coerceAtLeast(TreeLayout.CARD_H + 32f))
        val scaleDown = min(1f, min(MAX_EDGE_PX / contentW, MAX_EDGE_PX / contentH))
        val w = (contentW * scaleDown).toInt().coerceAtLeast(1)
        val h = (contentH * scaleDown).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scaleDown, scaleDown)
        canvas.drawColor(Color.parseColor("#0B1020"))

        val cardW = dp(TreeLayout.CARD_W)
        val cards = placement.cards.associateBy { it.id }
        fun rectOf(id: String): RectF? {
            val card = cards[id] ?: return null
            val left = dp(card.left)
            val top = dp(card.top)
            return RectF(left, top, left + cardW, top + dp(card.height))
        }
        fun genderColor(id: String): Int = when (byId[id]?.gender) {
            "m" -> Color.parseColor("#42A5F5")
            "f" -> Color.parseColor("#FF7043")
            else -> Color.parseColor("#7E57C2")
        }

        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.4f)
            strokeCap = Paint.Cap.ROUND
        }
        val hubPaint = Paint(linkPaint)

        val minDrop = dp(24f)
        placement.childGroups.forEach { group ->
            val parentRects = group.parentIds.mapNotNull { id -> rectOf(id)?.let { id to it } }
            val childRects = group.childIds.mapNotNull { id -> rectOf(id)?.let { id to it } }
            if (parentRects.isEmpty() || childRects.isEmpty()) return@forEach
            val parentBoxes = parentRects.map { (_, r) ->
                TreeLinkGeometry.Box(r.left, r.top, r.right, r.bottom)
            }
            val (unionX, unionY) = TreeLinkGeometry.unionHub(parentBoxes)
            val childTop = childRects.minOf { it.second.top }
            val stemY = TreeLinkGeometry.unionStemY(unionY, childTop, minDrop)
            val childCenters = childRects.map { it.second.centerX() }
            val busLeft = childCenters.min()
            val busRight = childCenters.max()
            linkPaint.color = genderColor(group.parentIds.first())
            canvas.drawLine(unionX, unionY, unionX, stemY, linkPaint)
            if (childRects.size > 1) {
                if (unionX < busLeft) canvas.drawLine(unionX, stemY, busLeft, stemY, linkPaint)
                if (unionX > busRight) canvas.drawLine(unionX, stemY, busRight, stemY, linkPaint)
                canvas.drawLine(busLeft, stemY, busRight, stemY, linkPaint)
            }
            childRects.forEach { (childId, child) ->
                linkPaint.color = genderColor(childId)
                val fromX = if (childRects.size > 1) child.centerX() else unionX
                canvas.drawLine(fromX, stemY, child.centerX(), child.top, linkPaint)
            }
        }

        placement.spouseLinks.forEach { (first, second) ->
            val start = rectOf(first) ?: return@forEach
            val end = rectOf(second) ?: return@forEach
            val left = if (start.left <= end.left) start else end
            val right = if (start.left <= end.left) end else start
            linkPaint.color = genderColor(if (start.left <= end.left) first else second)
            canvas.drawLine(left.right, left.centerY(), right.left, right.centerY(), linkPaint)
            val midX = (left.right + right.left) / 2f
            val midY = (left.centerY() + right.centerY()) / 2f - dp(8f)
            canvas.drawCircle(midX, midY, dp(4f), hubPaint.apply { color = Color.WHITE })
        }

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2838") }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F7FA")
            textSize = dp(13f)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AA4B2")
            textSize = dp(11f)
        }

        placement.cards.forEach { card ->
            val person = byId[card.id] ?: return@forEach
            val rect = rectOf(card.id) ?: return@forEach
            val radius = dp(12f)
            canvas.drawRoundRect(rect, radius, radius, cardFill)
            cardStroke.color = genderColor(card.id)
            canvas.drawRoundRect(rect, radius, radius, cardStroke)

            val pad = dp(8f)
            val textW = (rect.width() - pad * 2).toInt().coerceAtLeast(1)
            val name = if (person.isVacantSlot()) "Пустое место" else person.fullName()
            val layout = StaticLayout.Builder
                .obtain(name, 0, name.length, titlePaint, textW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .build()
            var textY = rect.top + pad + dp(4f)
            canvas.save()
            canvas.translate(rect.left + pad, textY)
            layout.draw(canvas)
            canvas.restore()
            textY += layout.height + dp(2f)
            val years = person.yearsLabel()
            if (years.isNotBlank()) {
                canvas.drawText(years, rect.centerX(), textY + subPaint.textSize, subPaint.apply {
                    textAlign = Paint.Align.CENTER
                })
            }
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = dp(10f)
        }
        val footer = "TreeX · ${placement.cards.size} карточек · ${placement.width.toInt()}×${placement.height.toInt()} dp"
        canvas.drawText(footer, dp(12f), contentH - dp(10f), footerPaint)
        return bitmap
    }
}
