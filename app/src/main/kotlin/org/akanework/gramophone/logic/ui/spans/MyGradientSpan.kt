package org.akanework.gramophone.logic.ui.spans

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import kotlin.math.max
import kotlin.math.min
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv

// Hacks, hacks, hacks...
class MyGradientSpan(val gradientWidth: Float, color: Int, highlightColor: Int) : CharacterStyle(),
    UpdateAppearance {
    private val matrix = Matrix()
    private val shader = LinearGradient(
        0f, 1f, gradientWidth, 1f,
        //highlightColor, color, TODO
        Color.GREEN, Color.YELLOW,
        Shader.TileMode.CLAMP
    )
    var progress = 1f
    lateinit var lineOffsets: List<Int>
    var lineCountDivider = 0
    var totalCharsForProgress = 0
    var lineCount = 0
    override fun updateDrawState(tp: TextPaint) {
        tp.color = Color.WHITE
        val o = 5 * ((lineCount / lineCountDivider) % (lineOffsets.size / 5))
        val preOffsetFromLeft = lineOffsets[o].toFloat()
        val textLength = lineOffsets[o + 1]
        val isRtl = lineOffsets[o + 4] == -1
        val ourProgress = lerpInv(lineOffsets[o + 2].toFloat(), lineOffsets[o + 3].toFloat(),
            lerp(0f, totalCharsForProgress.toFloat(), progress)).coerceIn(0f, 1f)
        val ourProgressD = if (isRtl) 1f - ourProgress else ourProgress
        shader.setLocalMatrix(matrix.apply {
            // step 0: gradient is |>>-------| where > is the gradient and - is clamping color
            reset()
            val overhangToRemove = ((textLength + gradientWidth) * ourProgress - textLength).coerceIn(0f, gradientWidth - 1)
            // We never want to go above textLength x position so calculate out eventual overhead
            // we have to remove from gradientWidth with scaling.
            val desiredWidth = (gradientWidth - overhangToRemove).coerceIn(1f, gradientWidth)
            // step 1: if we need to squish the gradient, do it first: |>--------|
            postScale((1f / gradientWidth) * desiredWidth, 1f)
            // step 4: if we are in RTL mode, rotate from |---->>--| to |--<<----|
            if (isRtl) postRotate(180f, gradientWidth / 2f, 1f)
            // step 2: Move the gradient start to end of completed text
            // Example without squishing (not at end line yet): |---->>--|
            // Example with squishing: |------->|
            postTranslate(textLength * lineOffsets[o + 4] * 1f /* ourProgressD TODO*/, 0f)
            // step 3: we want the end of the gradient be at the end of the completed text (AKA the
            // start of the non-completed text) so that the non-completed text is not colored. so,
            // subtract the gradient width again to move the gradient end to prev. gradient start
            postTranslate(desiredWidth * (1f - ourProgressD), 0f)
            // step 5: the left offset is always from the left so don't rotate that :D
            postTranslate(preOffsetFromLeft, 0f)
        })
        tp.shader = shader
        lineCount++
    }
}