package org.akanework.gramophone.ui

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Bundle
import android.text.Layout
import android.text.TextPaint
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.parseLrc
import org.akanework.gramophone.ui.components.NewLyricsView

class ContributorsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeProperly()
        val p = Paint()
        val rs = RuntimeShader("""
uniform float height;
uniform float width;
uniform float iTime;

vec4 main(vec2 fragCoord) {
    vec2 xy = vec2(0, 0);
    xy.x = width;
    xy.y = height;
    vec2 uv = (2.0 * fragCoord - xy.xy) / min(xy.x, xy.y);

    for (float i = 1.0; i < 10.0; i ++) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + iTime);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + iTime);
    }
    
    vec4 fragColor = vec4( vec3(0.1) / abs( sin( iTime - uv.y - uv.x ) ), 1.0);
    
    return fragColor;
}
        """)
        p.shader = rs
        val start = System.currentTimeMillis()
        val fl = FrameLayout(this)
        val matrix = Matrix()
        val tp = TextPaint()
        val camera = Camera()
        tp.color = Color.RED
        tp.textSize = 100f
        tp.textAlign = Paint.Align.CENTER
        fl.addView(object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val time = (System.currentTimeMillis() - start).toFloat() / 1000f
                rs.setFloatUniform("height", height.toFloat())
                rs.setFloatUniform("width", width.toFloat())
                rs.setFloatUniform("iTime", time)
                canvas.drawPaint(p)
                canvas.save()
                camera.save()
                camera.rotate(CalculationUtils.lerp(-90f, 0f, (time % 10f) / 10f), CalculationUtils.lerp(90f, 0f, (time % 10f) / 10f), 0f)
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
        })
        /*val lv = NewLyricsView(this, null)
        lv.updateTextColor(Color.WHITE, Color.RED, Color.TRANSPARENT, Color.TRANSPARENT,
            Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT)
        lv.instance = object : NewLyricsView.Callbacks {
            override fun getCurrentPosition(): ULong {
                return ((System.currentTimeMillis() - start) % 5000).toULong()
            }

            override fun seekTo(position: ULong) {
                // do nothing
            }
        }
        lv.updateLyrics(parseLrc("[00:00.00]Gramophone<00:02.00>", false, false))
        fl.addView(lv)*/
        setContentView(fl)
    }
}