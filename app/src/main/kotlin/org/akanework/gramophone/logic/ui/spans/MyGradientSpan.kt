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
        highlightColor, color,
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
        val ourProgressLtr = max(
            0f, min(
                1f, lerpInv(
                    lineOffsets[o + 2].toFloat(), lineOffsets[o + 3]
                        .toFloat(), lerp(0f, totalCharsForProgress.toFloat(), progress)
                )
            )
        )
        val ourProgress = if (lineOffsets[o + 4] == -1) 1f - ourProgressLtr else ourProgressLtr
        shader.setLocalMatrix(matrix.apply {
            reset()
            //val overhangToRemove = max(0f, min(((lineOffsets[o + 1] + gradientWidth) * ourProgress) - lineOffsets[o + 1], gradientWidth - 1))
            val overhangToRemove = gradientWidth - 1f
            postScale((1f / gradientWidth) * (gradientWidth - overhangToRemove), 1f)
            if (lineOffsets[o + 4] == -1) postRotate(180f, gradientWidth / 2f, 1f)
            postTranslate(preOffsetFromLeft + lineOffsets[o + 1] * ourProgress, 0f)
            postTranslate(gradientWidth * (ourProgress - 1f), 0f)
        })
        tp.shader = shader
        lineCount++
    }
}