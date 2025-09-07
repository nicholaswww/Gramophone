@file:Suppress("SameReturnValue")

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp

private inline val padding
    get() = 160f
private inline val size
    get() = 960f
private inline val barWidth
    get() = 160f
private inline val barHeight
    get() = 640
private inline val barHeightMin
    get() = 30
private inline val animDuration
    get() = 200f

class NowPlayingDrawable(context: Context) : Drawable() {

    private val paint = Paint()
    private val rng = Random
    private var animationsEnabled: Boolean? = null
    var level2Done: Runnable? = null
    private var sx: Float = 1f // scale x
    private var sy: Float = 1f // scale y
    private var lc: Float = 0f // left current
    private var li: Float = 0f // left initial
    private var lt: Float = 0f // left target
    private var mc: Float = 0f // middle current
    private var mi: Float = 0f // middle initial
    private var mt: Float = 0f // middle target
    private var rc: Float = 0f // right current
    private var ri: Float = 0f // right initial
    private var rt: Float = 0f // right target
    private var ts: Long = 0L

    init {
        CoroutineScope(Dispatchers.Default).launch {
            val animationsEnabled = Settings.Global.getFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
            withContext(Dispatchers.Main) {
                this@NowPlayingDrawable.animationsEnabled = animationsEnabled
                if (animationsEnabled) ts = System.currentTimeMillis()
                invalidateSelf()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val ld = if (lc == lt && level == 1) {
            lt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin; li = lc; true
        } else false
        val md = if (mc == mt && level == 1) {
            mt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin; mi = mc; true
        } else false
        val rd = if (rc == rt && level == 1) {
            rt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin; ri = rc; true
        } else false
        if ((ld || md || rd) && animationsEnabled == true) ts = System.currentTimeMillis()
        val scale = if (animationsEnabled == true)
                ((System.currentTimeMillis() - ts) / animDuration).coerceAtMost(1f)
        else if (animationsEnabled == false) 1f else 0f

        // Left bar
        lc = lerp(li, lt, scale)
        canvas.drawBar(0f, lc)

        // Middle bar
        mc = lerp(mi, mt, scale)
        canvas.drawBar(240f, mc)

        // Right bar
        rc = lerp(ri, rt, scale)
        canvas.drawBar(480f, rc)

        if (level != 1 && lc == lt && mc == mt && rc == rt) ts = 0L
        if (ts != 0L) invalidateSelf() else if (level == 2 && level2Done != null)
            scheduleSelf(level2Done!!, 0)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Canvas.drawBar(left: Float, height: Float) {
        drawRect(
            (padding + left) * sx, ((size - padding) - height) * sy,
            (padding + left + barWidth) * sx, (size - padding) * sy, paint
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        sx = bounds.width() / size
        sy = bounds.height() / size
        tr.set(0, 0, (size * sx).toInt(), (size * sy).toInt())
        tr.op(
            (padding * sx).toInt(), (padding * sy).toInt(), ((size - padding) * sx).toInt(),
            ((size - padding) * sy).toInt(), Region.Op.DIFFERENCE
        )
    }

    // --- lots of boilerplate below ---
    @ColorInt
    private var tintColor: Int? = null
    private var tintMode: PorterDuff.Mode? = null
    private val tr = Region()

    override fun getTransparentRegion(): Region {
        return tr
    }

    override fun onLevelChange(level: Int): Boolean {
        li = lc
        mi = mc
        ri = rc
        when (level) {
            0 -> {
                lt = barHeightMin.toFloat()
                mt = barHeightMin.toFloat()
                rt = barHeightMin.toFloat()
            }

            1 -> {
                lt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin
                mt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin
                rt = rng.nextInt(barHeight - barHeightMin).toFloat() + barHeightMin
            }

            2 -> {
                lt = 0f
                mt = 0f
                rt = 0f
            }

            else -> throw IllegalStateException()
        }
        ts = System.currentTimeMillis()
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getAlpha(): Int {
        return paint.alpha
    }

    override fun setTintList(tint: ColorStateList?) {
        setTint(tint?.getColorForState(null, Color.BLACK) ?: Color.BLACK)
    }

    override fun setTint(tintColor: Int) {
        this.tintColor = tintColor
        paint.colorFilter = if (tintMode == null) null else
            PorterDuffColorFilter(tintColor, tintMode!!)
        paint.color = if (tintMode == null) tintColor else Color.WHITE
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.tintColor = null
        this.tintMode = null
        paint.color = if (colorFilter == null) Color.BLACK else Color.WHITE
        paint.colorFilter = colorFilter
    }

    override fun getColorFilter(): ColorFilter? {
        return if (tintMode != null) null else paint.colorFilter
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        this.tintMode = tintMode
        paint.colorFilter = if (tintColor == null || tintMode == null) null else
            PorterDuffColorFilter(tintColor!!, tintMode)
        paint.color = if (tintColor == null) Color.BLACK else
            if (tintMode == null) tintColor!! else Color.WHITE
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int =
        // Must be PixelFormat.UNKNOWN, TRANSLUCENT, TRANSPARENT, or OPAQUE
        PixelFormat.TRANSLUCENT
}
