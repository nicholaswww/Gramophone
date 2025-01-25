package org.akanework.gramophone.logic.ui.spans

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv

// Hacks, hacks, hacks...
class MyGradientSpan(grdWidth: Float, color: Int, highlightColor: Int) : CharacterStyle(),
    UpdateAppearance {
    private val matrix = Matrix()
    private val gradientWidth = grdWidth
    private val shader = LinearGradient(
        0f, 1f, gradientWidth, 1f,
        highlightColor, color,
        //Color.GREEN, Color.YELLOW,
        Shader.TileMode.CLAMP
    )
    var progress = 1f
    lateinit var lineOffsets: List<Int>
    lateinit var runToLineMappings: List<Int>
    var totalCharsForProgress = 0
    var runCount = 0
    var lastLineCount = -1
    override fun updateDrawState(tp: TextPaint) {
        tp.color = Color.WHITE
        val o = 5 * ((runToLineMappings[runCount % runToLineMappings.size]) % (lineOffsets.size / 5))
        if (o != lastLineCount) {
            val preOffsetFromLeft = lineOffsets[o].toFloat()
            val textLength = lineOffsets[o + 1]
            val isRtl = lineOffsets[o + 4] == -1
            val ourProgress = lerpInv(lineOffsets[o + 2].toFloat(), lineOffsets[o + 3].toFloat(),
            lerp(0f, totalCharsForProgress.toFloat(), progress)).coerceIn(0f, 1f)
            val ourProgressD = if (isRtl) 1f - ourProgress else ourProgress
            shader.setLocalMatrix(matrix.apply {
                // step 0: gradient is |>>-------| where > is the gradient and - is clamping color
                reset()
                val vPosOfEndIfFullWidth = textLength * ourProgressD + gradientWidth * ourProgressD
                val overhangToRemove = (vPosOfEndIfFullWidth - textLength).coerceIn(0f, gradientWidth - 1f)
                val vPosOfStartIfFullWidth = vPosOfEndIfFullWidth - gradientWidth
                val underhangToRemove = (-vPosOfStartIfFullWidth).coerceIn(0f, gradientWidth - 1f)
                // We never want to go above textLength x position so calculate out eventual overhead
                // we have to remove from gradientWidth with scaling.
                val desiredWidth = (gradientWidth - overhangToRemove - underhangToRemove).coerceIn(1f, gradientWidth)
                // step 1: if we need to squish the gradient, do it first: |>--------|
                postScale(desiredWidth / gradientWidth, 1f)
                // step 2: if we are in RTL mode, rotate from |---->>--| to |--<<----|
                if (isRtl) postRotate(180f, desiredWidth / 2f, 1f)
                // step 3: Move the gradient start to end of completed text
                // Example without squishing (not at end line yet): |---->>--|
                // Example with squishing: |------->|
                postTranslate(textLength * ourProgressD, 0f)
                // step 4: gradually transition
                postTranslate(desiredWidth * (ourProgressD - 1f), 0f)
                // step 5: the left offset is always from the left so don't rotate that :D
                postTranslate(preOffsetFromLeft, 0f)
            })
        }
        tp.shader = shader
        lastLineCount = o
        runCount++
    }
}