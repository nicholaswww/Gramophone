package org.akanework.gramophone.ui.fragments.settings

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class ContributorsSettingsFragment : BaseSettingFragment(
    R.string.settings_contributors, { ContributorsFragment() })

class ContributorsFragment : BaseFragment(null) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val start = System.currentTimeMillis()
        val matrix = Matrix()
        val tp = TextPaint()
        val camera = Camera()
        tp.color = Color.RED
        tp.textSize = 100f
        tp.textAlign = Paint.Align.CENTER
        return object : View(requireActivity()) {
            override fun onDraw(canvas: Canvas) {
                val time = (System.currentTimeMillis() - start).toFloat() / 1000f
                canvas.save()
                camera.save()
                camera.rotate(lerp(-90f, 0f, (time % 10f) / 10f), lerp(90f, 0f, (time % 10f) / 10f), 0f)
                matrix.reset()
                camera.getMatrix(matrix)
                camera.restore()
                matrix.preTranslate(width / 2f, 50f)
                //matrix.postTranslate(width / 2f, 0f)
                canvas.concat(matrix)
                StaticLayoutBuilderCompat.obtain("Gramophone\n" +
                        "v${BuildConfig.VERSION_NAME}\nBy nift4, 123Duo3, AkaneTan, WSTxda, imjyotiraditya", tp, width)
                    .build()
                    .draw(canvas)
                canvas.restore()
                invalidate()
            }
        }
    }
}