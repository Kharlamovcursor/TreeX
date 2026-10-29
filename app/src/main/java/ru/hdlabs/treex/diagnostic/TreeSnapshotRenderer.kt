package ru.hdlabs.treex.diagnostic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.runBlocking
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.isVacantSlot
import ru.hdlabs.treex.domain.layout.EdgeType
import ru.hdlabs.treex.domain.layout.LayoutConfig
import ru.hdlabs.treex.domain.layout.LayoutEdge
import ru.hdlabs.treex.domain.layout.SugiyamaLayout
import ru.hdlabs.treex.domain.layout.TreeLayout
import ru.hdlabs.treex.domain.yearsLabel
import kotlin.math.min

/** Static bitmap of Sugiyama layout for debug Git uploads (android.graphics only). */
object TreeSnapshotRenderer {
    private const val MAX_EDGE_PX = 4096

    fun renderPng(
        context: Context,
        people: List<PersonEntity>,
        unions: List<UnionRecord>,
        openLinks: List<OpenLink>,
        homePersonId: String?,
    ): ByteArray {
        @Suppress("UNUSED_PARAMETER")
        val unusedOpenLinks = openLinks
        @Suppress("UNUSED_PARAMETER")
        val unusedHome = homePersonId
        @Suppress("UNUSED_PARAMETER")
        val unusedUnions = unions
        val density = context.resources.displayMetrics.density.coerceIn(1.5f, 4f)
        val db = AppDatabase.getInstance(context)
        val graph = runBlocking {
            GenealogyGraphMapper.fromFamilies(
                people,
                db.familyDao().all(),
                db.familyChildDao().all(),
            )
        }
        val layout = SugiyamaLayout.layout(graph)
        val livingById = people.filter { !it.vacant }.associateBy { it.id }
        val bitmap = renderBitmap(layout, livingById, density)
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            out.toByteArray()
        }
    }

    internal fun renderBitmap(
        layout: TreeLayout,
        byId: Map<String, PersonEntity>,
        density: Float,
    ): Bitmap {
        fun dp(v: Float): Float = v * density
        val pad = LayoutConfig.blockGap
        val contentW = dp((layout.bounds.maxX - layout.bounds.minX + pad * 2).coerceAtLeast(LayoutConfig.nodeWidth + pad))
        val contentH = dp((layout.bounds.maxY - layout.bounds.minY + pad * 2).coerceAtLeast(LayoutConfig.nodeHeight + pad))
        val scaleDown = min(1f, min(MAX_EDGE_PX / contentW, MAX_EDGE_PX / contentH))
        val w = (contentW * scaleDown).toInt().coerceAtLeast(1)
        val h = (contentH * scaleDown).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scaleDown, scaleDown)
        canvas.drawColor(Color.parseColor("#0B1020"))

        val offsetX = dp(pad - layout.bounds.minX)
        val offsetY = dp(pad - layout.bounds.minY)

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(LayoutConfig.edgeStrokeWidth)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#9AA4B2")
        }
        val hubPaint = Paint(edgePaint).apply { color = Color.parseColor("#E5E7EB") }

        layout.edges.forEach { edge ->
            drawEdge(canvas, edge, offsetX, offsetY, ::dp, edgePaint)
        }
        layout.familyBlocks.forEach { block ->
            val cx = offsetX + dp(block.centerX)
            val cy = offsetY + dp(block.bottomY)
            canvas.drawCircle(cx, cy, dp(4f), hubPaint)
        }

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2838") }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(LayoutConfig.edgeStrokeWidth)
            color = Color.parseColor("#6B7280")
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F7FA")
            textSize = dp(13f)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AA4B2")
            textSize = dp(11f)
        }

        layout.nodes.forEach { node ->
            val person = byId[node.personId] ?: return@forEach
            val left = offsetX + dp(node.x)
            val top = offsetY + dp(node.y)
            val rect = RectF(left, top, left + dp(node.width), top + dp(node.height))
            val radius = dp(12f)
            canvas.drawRoundRect(rect, radius, radius, cardFill)
            canvas.drawRoundRect(rect, radius, radius, cardStroke)

            val padIn = dp(8f)
            val textW = (rect.width() - padIn * 2).toInt().coerceAtLeast(1)
            val name = if (person.isVacantSlot()) "Пустое место" else person.fullName()
            val nameLayout = StaticLayout.Builder
                .obtain(name, 0, name.length, titlePaint, textW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .build()
            canvas.save()
            canvas.translate(rect.left + padIn, rect.top + padIn + dp(4f))
            nameLayout.draw(canvas)
            canvas.restore()
            val years = person.yearsLabel()
            if (years.isNotBlank()) {
                val textY = rect.top + padIn + dp(4f) + nameLayout.height + dp(2f)
                canvas.drawText(
                    years,
                    rect.centerX(),
                    textY + subPaint.textSize,
                    subPaint.apply { textAlign = Paint.Align.CENTER },
                )
            }
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = dp(10f)
        }
        val footer = "TreeX · ${layout.nodes.size} карточек · Sugiyama snapshot"
        canvas.drawText(footer, dp(12f), contentH - dp(10f), footerPaint)
        return bitmap
    }

    private fun drawEdge(
        canvas: Canvas,
        edge: LayoutEdge,
        offsetX: Float,
        offsetY: Float,
        dp: (Float) -> Float,
        basePaint: Paint,
    ) {
        val points = edge.points
        if (points.size < 2) return
        val paint = Paint(basePaint)
        if (edge.type == EdgeType.LEGACY_PARENT_CHILD) {
            paint.pathEffect = DashPathEffect(floatArrayOf(paint.strokeWidth * 3f, paint.strokeWidth * 2f), 0f)
        }
        val path = Path()
        val first = points.first()
        path.moveTo(offsetX + dp(first.x), offsetY + dp(first.y))
        points.drop(1).forEach { p ->
            path.lineTo(offsetX + dp(p.x), offsetY + dp(p.y))
        }
        canvas.drawPath(path, paint)
    }
}
