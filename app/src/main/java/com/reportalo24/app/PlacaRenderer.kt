package com.reportalo24.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera la placa de noticia como Bitmap (porta la lógica del generador web a
 * Canvas de Android). Produce PNG con foto o capa transparente para video.
 * Con la foto centrada y zoom 1 el resultado es equivalente al generador web.
 */
object PlacaRenderer {

    data class Opciones(
        val formato: FormatoPlaca,
        val foto: Bitmap? = null,
        val logo: Bitmap? = null,
        val titulo: String,
        val subtitulo: String,
        val franja: String = "",
        val mostrarFranja: Boolean = true,
        val categoria: String = "NINGUNA",
        val usuario: String = "@reportalo24",
        val posicion: Int = 50,
        val subSize: Int = 38,
        val tituloSize1: Int = 0,
        val tituloSize2: Int = 0,
        val imgZoom: Float = 1f,
        val imgOffX: Float = 0f,
        val imgOffY: Float = 0f,
        val conFoto: Boolean = true,
        val overrideW: Int? = null,
        val overrideH: Int? = null,
    )

    private data class Token(val text: String, val hot: Boolean, val isBreak: Boolean = false)

    private fun typefaceBold() = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private fun typefaceNormal() = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    fun render(o: Opciones): Bitmap {
        val W = o.overrideW ?: o.formato.w
        val H = o.overrideH ?: o.formato.h
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 1. Foto a pantalla completa (solo con foto)
        if (o.conFoto) {
            if (o.foto != null) {
                drawCover(c, o.foto, W, H, o.imgZoom, o.imgOffX, o.imgOffY)
            } else {
                c.drawColor(Color.rgb(16, 32, 31))
            }
        }

        // 2. Degradado decorativo (solo adorno: nada importante va en estas zonas)
        val gTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, H * 0.22f, Color.argb(90, 15, 90, 90), Color.argb(0, 15, 90, 90), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H * 0.22f, gTop)
        val gBot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, H * 0.60f, 0f, H.toFloat(), Color.argb(0, 11, 70, 70), Color.argb(190, 11, 70, 70), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, H * 0.60f, W.toFloat(), H.toFloat(), gBot)

        // 3. Tarjeta editorial centrada dentro de la zona segura de Reels/feed
        val teal = Color.rgb(26, 139, 139)
        val ink = Color.rgb(22, 50, 58)
        val red = Color.rgb(214, 31, 44)
        val cardW = W * 0.86f
        val cardX = (W - cardW) / 2f
        val inner = cardW - 112f
        val stack = o.formato == FormatoPlaca.STORY

        // Etiqueta (enganche): la franja; si no hay, la categoría
        val tagText = when {
            o.mostrarFranja && o.franja.isNotBlank() -> o.franja.trim().uppercase()
            o.categoria.isNotBlank() && o.categoria != "NINGUNA" -> o.categoria.trim().uppercase()
            else -> ""
        }
        var tagSize = 34f
        while (tagSize > 20f && textPaint(tagSize, true).measureText(tagText) + tagSize * 3.2f > cardW - 80f) tagSize -= 2f
        val tagH = if (tagText.isEmpty()) 0f else tagSize * 1.9f

        // Título (2 líneas) y subtítulo
        val (l1Text, l2Text) = splitTitle(o.titulo.trim().uppercase(), inner)
        val startTitle = if (o.formato == FormatoPlaca.STORY) 84 else 72
        val l1 = fitSingle(l1Text, if (o.tituloSize1 > 0) minOf(o.tituloSize1, startTitle + 12) else startTitle, 26, inner)
        val l2 = fitSingle(l2Text, if (o.tituloSize2 > 0) minOf(o.tituloSize2, startTitle + 12) else startTitle, 26, inner)
        val titleLines = listOf(l1, l2).filter { it.text.isNotBlank() }
        fun lineBox(size: Float) = size * 1.12f
        val subTokens = if (o.subtitulo.isNotBlank()) parseTokens(o.subtitulo) else emptyList()
        val subFit = if (subTokens.isNotEmpty()) fitTokenLines(subTokens, min(o.subSize, 40), 22, 4, 18, inner)
        else FitTokens(emptyList(), 30f)
        val subLH = subFit.size * 1.32f

        val tabH = 92f
        val ruleBlock = 12f + 30f
        val titleH = titleLines.sumOf { lineBox(it.size).toDouble() }.toFloat()
        val divBlock = if (subFit.lines.isNotEmpty()) 36f + 22f + 30f else 0f
        val subH = subFit.lines.size * subLH
        val cardH = tagH / 2f + 40f + ruleBlock + titleH + divBlock + subH + 44f + tabH

        // Centrado vertical: en Reel, en la franja segura (16 %–62 % de alto); en feed/carrusel, al centro
        // Dos posiciones: centro (posicion >= 25) o abajo (posicion < 25), ambas dentro de la zona segura
        val abajo = o.posicion < 25
        val top = if (stack) {
            if (abajo) H - 70f - cardH else H * 0.38f - cardH / 2f
        } else {
            if (abajo) H - 56f - cardH else H * 0.50f - cardH / 2f
        }.coerceAtLeast(tagH / 2f + 12f)
        val bottom = top + cardH
        val cardR = RectF(cardX, top, cardX + cardW, bottom)

        // Marco desplazado (profundidad) y tarjeta blanca con sombra
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = teal }
        c.drawRect(RectF(cardR.left + 24f, cardR.top + 24f, cardR.right + 24f, cardR.bottom + 24f), frame)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(34f, 0f, 14f, Color.argb(120, 0, 0, 0)) }
        c.drawRect(cardR, white)

        var y = top + tagH / 2f + 40f
        val cx = W / 2f
        // Raya roja gruesa
        val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
        c.drawRect(cx - 62f, y, cx + 62f, y + 12f, redP)
        y += ruleBlock

        // Título
        for (tl in titleLines) {
            val p = textPaint(tl.size, true).apply { color = ink; textAlign = Paint.Align.CENTER }
            val fm = p.fontMetrics
            val box = lineBox(tl.size)
            c.drawText(tl.text, cx, y + (box - (fm.descent - fm.ascent)) / 2f - fm.ascent, p)
            y += box
        }

        // Divisor fino con rombo + subtítulo
        if (subFit.lines.isNotEmpty()) {
            y += 36f
            val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(207, 227, 227) }
            val my = y + 11f
            c.drawRect(cardR.left + 70f, my - 1.5f, cx - 22f, my + 1.5f, soft)
            c.drawRect(cx + 22f, my - 1.5f, cardR.right - 70f, my + 1.5f, soft)
            val d = Path().apply { moveTo(cx, my - 11f); lineTo(cx + 11f, my); lineTo(cx, my + 11f); lineTo(cx - 11f, my); close() }
            c.drawPath(d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal })
            y += 22f + 30f
            val base = textPaint(subFit.size, false)
            for (i in subFit.lines.indices) {
                val line = subFit.lines[i]
                val full = line.joinToString(" ") { it.text }
                var lx = cx - base.measureText(full) / 2f
                val fm = base.fontMetrics
                val ly = y + i * subLH + (subLH - (fm.descent - fm.ascent)) / 2f - fm.ascent
                for (j in line.indices) {
                    val tok = line[j]
                    val piece = tok.text + if (j < line.size - 1) " " else ""
                    base.color = if (tok.hot) red else Color.rgb(50, 75, 84)
                    c.drawText(piece, lx, ly, base)
                    lx += base.measureText(piece)
                }
            }
        }

        // Pestaña de marca pegada abajo de la tarjeta
        val tabTop = bottom - tabH
        c.drawRect(RectF(cardR.left, tabTop, cardR.right, bottom), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal })
        val brand = if (o.usuario.isBlank()) "REPÓRTALO 24" else "REPÓRTALO 24 · ${o.usuario.trim().uppercase()}"
        var bs = 32f
        while (bs > 20f && textPaint(bs, true).measureText(brand) + 70f > cardW - 80f) bs -= 2f
        val bp = textPaint(bs, true).apply { color = Color.WHITE }
        val bw = bp.measureText(brand)
        val icon = 52f
        val groupW = icon + 18f + bw
        val gx = cx - groupW / 2f
        val iy = tabTop + tabH / 2f
        if (o.logo != null) {
            val lw = icon * o.logo.width.toFloat() / o.logo.height.toFloat()
            c.drawBitmap(o.logo, null, RectF(gx, iy - icon / 2f, gx + lw, iy + icon / 2f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val fm = bp.fontMetrics
            c.drawText(brand, gx + lw + 18f, iy - (fm.ascent + fm.descent) / 2f, bp)
        } else {
            drawMic(c, gx + icon / 2f, iy, icon, teal)
            val fm = bp.fontMetrics
            c.drawText(brand, gx + icon + 18f, iy - (fm.ascent + fm.descent) / 2f, bp)
        }

        // Etiqueta roja sobre el borde superior de la tarjeta
        if (tagText.isNotEmpty()) {
            val tp = textPaint(tagSize, true)
            val dot = tagSize * 0.5f
            val tw = tp.measureText(tagText)
            val padX = tagSize * 0.95f
            val bwTag = min(padX * 2 + dot + tagSize * 0.45f + tw, cardW - 40f)
            val bx = cx - bwTag / 2f
            val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red; setShadowLayer(16f, 0f, 6f, Color.argb(120, 214, 31, 44)) }
            c.drawRoundRect(RectF(bx, top - tagH / 2f, bx + bwTag, top + tagH / 2f), tagH / 2f, tagH / 2f, pill)
            c.drawCircle(bx + padX + dot / 2f, top, dot / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val fm = tp.fontMetrics
            c.drawText(tagText, bx + padX + dot + tagSize * 0.45f, top - (fm.ascent + fm.descent) / 2f, tp)
        }

        return bmp
    }

    // ----------------------------------------------------------------- helpers

    private fun textPaint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) typefaceBold() else typefaceNormal()
        color = Color.WHITE
    }

    private fun drawCover(c: Canvas, img: Bitmap, W: Int, H: Int, zoom: Float, offX: Float, offY: Float) {
        val iw = img.width.toFloat(); val ih = img.height.toFloat()
        val scale = max(W / iw, H / ih) * zoom
        val sw = W / scale; val sh = H / scale
        val freeX = iw - sw; val freeY = ih - sh
        var sx = freeX / 2f + offX * freeX / 2f
        var sy = freeY / 2f + offY * freeY / 2f
        sx = sx.coerceIn(0f, max(0f, freeX)); sy = sy.coerceIn(0f, max(0f, freeY))
        val src = Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        val dst = Rect(0, 0, W, H)
        c.drawBitmap(img, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun parseTokens(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        fun pushWords(str: String, hot: Boolean) {
            val parts = str.split("\n")
            for (p in parts.indices) {
                if (p > 0) tokens.add(Token("", false, isBreak = true))
                parts[p].split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { tokens.add(Token(it, hot)) }
            }
        }
        val regex = Regex("\\*\\*(.+?)\\*\\*")
        var last = 0
        for (m in regex.findAll(text)) {
            if (m.range.first > last) pushWords(text.substring(last, m.range.first), false)
            pushWords(m.groupValues[1], true)
            last = m.range.last + 1
        }
        if (last < text.length) pushWords(text.substring(last), false)
        return tokens
    }

    private fun wrapTokens(tokens: List<Token>, paint: Paint, maxW: Float): List<List<Token>> {
        val lines = ArrayList<MutableList<Token>>()
        var current = ArrayList<Token>()
        var currentText = ""
        for (tk in tokens) {
            if (tk.isBreak) { lines.add(current); current = ArrayList(); currentText = ""; continue }
            val test = if (currentText.isEmpty()) tk.text else "$currentText ${tk.text}"
            if (paint.measureText(test) > maxW && currentText.isNotEmpty()) {
                lines.add(current); current = arrayListOf(tk); currentText = tk.text
            } else {
                current.add(tk); currentText = test
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines.add(current)
        return lines
    }

    private data class FitTokens(val lines: List<List<Token>>, val size: Float)

    private fun fitTokenLines(tokens: List<Token>, start: Int, min: Int, maxLines: Int, absMin: Int, maxW: Float): FitTokens {
        fun lineText(l: List<Token>) = l.joinToString(" ") { it.text }
        fun tryFit(size: Int): FitTokens? {
            val p = textPaint(size.toFloat(), false)
            val lines = wrapTokens(tokens, p, maxW)
            if (lines.size <= maxLines && lines.all { p.measureText(lineText(it)) <= maxW }) return FitTokens(lines, size.toFloat())
            return null
        }
        var size = start
        while (size >= min) { tryFit(size)?.let { return it }; size -= 4 }
        size = min - 2
        while (size >= absMin) { tryFit(size)?.let { return it }; size -= 2 }
        val p = textPaint(absMin.toFloat(), false)
        var lines = wrapTokens(tokens, p, maxW).toMutableList()
        if (lines.size > maxLines) lines = lines.take(maxLines).toMutableList()
        return FitTokens(lines, absMin.toFloat())
    }

    private data class FitLine(val text: String, val size: Float)

    private fun fitSingle(text: String, chosen: Int, floor: Int, maxW: Float): FitLine {
        if (text.isBlank()) return FitLine("", chosen.toFloat())
        var s = chosen
        while (s >= floor) {
            val p = textPaint(s.toFloat(), true)
            if (p.measureText(text) <= maxW) return FitLine(text, s.toFloat())
            s -= 2
        }
        val p = textPaint(floor.toFloat(), true)
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return FitLine(t.trimEnd() + "…", floor.toFloat())
    }

    private fun splitTitle(title: String, maxW: Float): Pair<String, String> {
        if (title.isEmpty()) return "" to ""
        if (title.contains("\n")) {
            val parts = title.split("\n")
            return parts[0].trim() to (parts.getOrNull(1) ?: "").trim()
        }
        // Corte automático a mitad por palabras
        val words = title.split(" ")
        if (words.size == 1) return title to ""
        val mitad = ceil(title.length / 2.0).toInt()
        var l1 = ""
        for (w in words) {
            if (l1.isNotEmpty() && (l1.length + 1 + w.length) > mitad) break
            l1 = if (l1.isEmpty()) w else "$l1 $w"
        }
        val l2 = title.substring(l1.length).trim()
        return l1 to l2
    }

    private fun drawTitleLine(c: Canvas, text: String, size: Float, cx: Float, baseline: Float) {
        if (text.isBlank()) return
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = typefaceBold(); textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, size * 0.06f); color = Color.argb((0.55f * 255).toInt(), 0, 0, 0)
        }
        c.drawText(text, cx, baseline, stroke)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = typefaceBold(); textAlign = Paint.Align.CENTER
            color = Color.WHITE; setShadowLayer(24f, 0f, 4f, Color.argb(242, 0, 0, 0))
        }
        c.drawText(text, cx, baseline, fill)
    }

    private fun drawPill(c: Canvas, text: String, size: Float, cx: Float, top: Float, h: Float, maxW: Float, centered: Boolean) {
        val p = textPaint(size, true)
        val tw = p.measureText(text)
        val padX = size * 0.94f
        val bw = min(tw + padX * 2, maxW)
        val bx = cx - bw / 2f
        val rr = 14f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 31, 44); setShadowLayer(10f, 0f, 0f, Color.argb(115, 0, 0, 0)) }
        c.drawRoundRect(RectF(bx, top, bx + bw, top + h), rr, rr, bg)
        val tp = textPaint(size, true).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val fm = tp.fontMetrics
        val ty = top + h / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, cx, ty, tp)
    }

    /** Micrófono de marca (círculo blanco con el micrófono en el color dado). */
    private fun drawMic(c: Canvas, cx: Float, cy: Float, d: Float, color: Int) {
        c.drawCircle(cx, cy, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = d * 0.07f; strokeCap = Paint.Cap.ROUND; this.color = color
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(RectF(cx - d * 0.11f, cy - d * 0.27f, cx + d * 0.11f, cy + d * 0.07f), d * 0.11f, d * 0.11f, fill)
        c.drawArc(RectF(cx - d * 0.2f, cy - d * 0.17f, cx + d * 0.2f, cy + d * 0.19f), 0f, 180f, false, st)
        c.drawLine(cx, cy + d * 0.19f, cx, cy + d * 0.29f, st)
    }

    private fun drawCategoria(c: Canvas, cat: String, W: Int) {
        val maxW = W * 0.42f
        val floor = 18; val baseSize = 30
        var fit = floor
        var s = baseSize
        while (s >= floor) {
            val p = textPaint(s.toFloat(), true)
            if (p.measureText(cat) + (s * 0.85f) * 2 <= maxW) { fit = s; break }
            s -= 2
        }
        val p = textPaint(fit.toFloat(), true)
        val padX = fit * 0.85f
        val h = fit * 1.9f
        val tw = p.measureText(cat)
        val bw = min(tw + padX * 2, maxW)
        val top = 90f; val right = 40f
        val bx = W - right - bw
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 31, 44); setShadowLayer(10f, 0f, 0f, Color.argb(115, 0, 0, 0)) }
        c.drawRoundRect(RectF(bx, top, bx + bw, top + h), 12f, 12f, bg)
        val tp = textPaint(fit.toFloat(), true).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val fm = tp.fontMetrics
        c.drawText(cat, bx + bw / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, tp)
    }
}
