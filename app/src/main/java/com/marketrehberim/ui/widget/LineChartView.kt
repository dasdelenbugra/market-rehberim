package com.marketrehberim.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors

/**
 * Kütüphaneye bağımlı olmayan, tema uyumlu basit çizgi grafik.
 *
 * Grafik yalnız çizmiyor, okunuyor: her nokta bir gün ve bir fiyat demek, ama
 * eksende etiket olmayınca kullanıcı "hangi gün ne kadardı" sorusunu
 * cevaplayamıyordu. Bu yüzden [Point] hem değeri hem de ekranda gösterilecek
 * **hazır metinleri** taşır — biçimlendirme (₺, tarih) çağıran tarafta kalsın,
 * View yerelleştirme bilmesin.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /**
     * @param value çizim için sayısal fiyat
     * @param priceLabel baloncukta gösterilecek biçimli fiyat ("38,90 ₺")
     * @param dateLabel eksende ve baloncukta gösterilecek kısa tarih ("14.07")
     */
    data class Point(val value: Float, val priceLabel: String, val dateLabel: String)

    private var points: List<Point> = emptyList()

    /** Dokunulan noktanın indeksi; -1 ise baloncuk çizilmez. */
    private var selected = -1

    /** Soldan sağa açılma oranı (0..1). Animasyon bunu sürer. */
    private var progress = 1f
    private var animator: ValueAnimator? = null

    private val lineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.GREEN)
    private val fillTop = (lineColor and 0x00FFFFFF) or 0x55000000
    private val gridColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.GRAY)
    private val labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
    private val bubbleColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceInverse, Color.DKGRAY)
    private val bubbleTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceInverse, Color.WHITE)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = lineColor
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lineColor
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (lineColor and 0x00FFFFFF) or 0x40000000
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = (gridColor and 0x00FFFFFF) or 0x33000000
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = (lineColor and 0x00FFFFFF) or 0x66000000
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = sp(10f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bubbleColor
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bubbleTextColor
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }

    /** Tarih etiketlerine ayrılan alt şerit; grafik alanı bu kadar yukarıda biter. */
    private val axisHeight = sp(10f) + dp(8f)

    fun setPoints(points: List<Point>) {
        val changed = points.map { it.value } != this.points.map { it.value }
        this.points = points
        selected = -1
        // Aynı veri tekrar gelirse (yapılandırma değişimi, yeniden abone olma)
        // animasyonu baştan oynatmak titrek görünür; sadece veri değiştiyse oynat.
        if (changed) animateIn() else invalidate()
    }

    private fun animateIn() {
        animator?.cancel()
        if (points.size < 2) {
            progress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val padV = dp(12f)
        val padH = dp(8f)
        val w = width - padH * 2
        val h = height - padV * 2 - axisHeight

        val values = points.map { it.value }
        val min = values.min()
        val max = values.max()
        val flat = max - min <= 0f
        val range = if (flat) 1f else max - min

        val stepX = w / (points.size - 1)
        fun x(i: Int) = padH + stepX * i
        // Fiyat hiç değişmediyse çizgi ortada dursun. Normal formül bu durumda
        // hepsini tabana yapıştırıyor ve grafik bozuk çizilmiş gibi görünüyordu.
        fun y(v: Float) =
            if (flat) padV + h / 2f else padV + h - ((v - min) / range) * h

        for (g in 0..2) {
            val gy = padV + h * g / 2f
            canvas.drawLine(padH, gy, padH + w, gy, gridPaint)
        }

        // Çizgi ve dolgu tek bir kırpma altında soldan sağa açılır. `PathMeasure`
        // ile segment kesmek yerine kırpma: dolgu da aynı anda ilerlesin diye
        // (iki ayrı yolu senkron kesmek gereksiz karmaşık olurdu).
        canvas.save()
        canvas.clipRect(0f, 0f, padH + w * progress, height.toFloat())

        val fill = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until points.size) lineTo(x(i), y(values[i]))
            lineTo(x(points.size - 1), padV + h)
            lineTo(x(0), padV + h)
            close()
        }
        fillPaint.shader = LinearGradient(
            0f, padV, 0f, padV + h, fillTop, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawPath(fill, fillPaint)

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until points.size) lineTo(x(i), y(values[i]))
        }
        canvas.drawPath(line, linePaint)

        canvas.drawCircle(x(0), y(values[0]), dp(3f), pointPaint)
        canvas.drawCircle(x(points.size - 1), y(values.last()), dp(4f), pointPaint)
        canvas.restore()

        drawAxisLabels(canvas, padH, w, height - dp(2f))

        // Animasyon sürerken baloncuk çizilmez: yarı açılmış bir grafikte
        // imlecin sağdaki henüz görünmeyen noktaya oturması tuhaf olurdu.
        if (progress >= 1f) drawSelection(canvas, ::x, ::y, padV, h)
    }

    /** Yalnız ilk ve son tarih: aradakiler bu genişlikte üst üste biner. */
    private fun drawAxisLabels(canvas: Canvas, padH: Float, w: Float, baseline: Float) {
        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(points.first().dateLabel, padH, baseline, axisPaint)
        axisPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(points.last().dateLabel, padH + w, baseline, axisPaint)
    }

    private fun drawSelection(
        canvas: Canvas,
        x: (Int) -> Float,
        y: (Float) -> Float,
        padV: Float,
        h: Float,
    ) {
        val i = selected.takeIf { it in points.indices } ?: return
        val point = points[i]
        val px = x(i)
        val py = y(point.value)

        canvas.drawLine(px, padV, px, padV + h, cursorPaint)
        canvas.drawCircle(px, py, dp(7f), haloPaint)
        canvas.drawCircle(px, py, dp(4f), pointPaint)

        val label = "${point.priceLabel} · ${point.dateLabel}"
        val textW = bubbleTextPaint.measureText(label)
        val padX = dp(8f)
        val bubbleW = textW + padX * 2
        val bubbleH = sp(11f) + dp(12f)

        // Baloncuk noktanın üstünde durur; tepeye yapışan noktalarda ekranın
        // dışına taşmasın diye alta düşer, kenarlarda da içeri sıkıştırılır.
        val above = py - dp(12f) - bubbleH >= 0f
        val top = if (above) py - dp(12f) - bubbleH else py + dp(12f)
        val left = (px - bubbleW / 2f).coerceIn(0f, width - bubbleW)
        val rect = RectF(left, top, left + bubbleW, top + bubbleH)

        canvas.drawRoundRect(rect, dp(8f), dp(8f), bubblePaint)
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f,
            bubbleTextPaint,
        )
    }

    /**
     * Dokunma yönü kararı. Grafik kaydırılabilir bir ekranın içinde duruyor:
     * her dokunuşta olayı sahiplenirse sayfa grafiğin üzerinden kaydırılamaz
     * hale gelir. Bu yüzden yön netleşene kadar kimse sahiplenmez.
     */
    private var downX = 0f
    private var downY = 0f
    private var scrubbing = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.size < 2) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                scrubbing = false
                // Seçim burada yapılmaz: parmak aşağı kaydırmaya gidiyorsa
                // baloncuk bir an görünüp kaybolurdu.
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!scrubbing) {
                    // Dikey hareket önce eşiği aşarsa dokunmayı hiç sahiplenmeyiz;
                    // NestedScrollView olayı kapar ve bize ACTION_CANCEL gelir.
                    if (kotlin.math.abs(dy) > touchSlop &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx)
                    ) {
                        return false
                    }
                    if (kotlin.math.abs(dx) <= touchSlop) return true
                    scrubbing = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                selectAt(event.x)
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Kaydırma değil, düz dokunuş: o noktanın fiyatını göster.
                if (!scrubbing) {
                    selectAt(event.x)
                    performClick()
                } else {
                    clearSelection()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                clearSelection()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun selectAt(touchX: Float) {
        val padH = dp(8f)
        val w = width - padH * 2
        val stepX = w / (points.size - 1)
        // Yuvarlama: parmağa en yakın nokta seçilsin, soldaki değil.
        val index = Math.round((touchX - padH) / stepX).coerceIn(0, points.size - 1)
        if (index != selected) {
            selected = index
            invalidate()
        }
    }

    private fun clearSelection() {
        if (selected == -1) return
        selected = -1
        invalidate()
    }

    /** Dokunma geri bildirimi ve erişilebilirlik için; `onTouchEvent` uyarısını da kapatır. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /**
     * `displayMetrics.scaledDensity` API 34'te kullanımdan kaldırıldı (doğrusal
     * olmayan yazı ölçeğini yansıtmıyor); `applyDimension` doğru dönüşümü yapar.
     */
    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )
}