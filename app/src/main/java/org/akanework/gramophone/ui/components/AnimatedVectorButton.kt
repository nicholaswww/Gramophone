package org.akanework.gramophone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import org.akanework.gramophone.R

class AnimatedVectorButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var drawable: Drawable? = null
    private var isAnimatedDrawable = false

    private var iconSize = 0

    init {
        isClickable = true
        isFocusable = true

        context.obtainStyledAttributes(attrs, R.styleable.AnimatedVectorButton).apply {
            getDrawable(R.styleable.AnimatedVectorButton_icon)?.let { setDrawable(it) }
            iconSize = getDimensionPixelSize(R.styleable.AnimatedVectorButton_iconSize, 0)
            recycle()
        }
    }

    fun setDrawable(resId: Int) =
        AppCompatResources.getDrawable(context, resId)?.let { setDrawable(it) }

    fun setDrawable(drawable: Drawable) {
        this.drawable = drawable
        isAnimatedDrawable = this.drawable is AnimatedVectorDrawable
    }

    fun setDrawableColor(color: Int) {
        drawable?.setTint(color)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawable?.draw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        drawable?.let { d ->
            val intrinsicWidth = d.intrinsicWidth
            val intrinsicHeight = d.intrinsicHeight

            if (iconSize <= 0) {
                d.setBounds(
                    (w / 2 - intrinsicWidth / 2),
                    (h / 2 - intrinsicHeight / 2),
                    (w / 2 + intrinsicWidth / 2),
                    (h / 2 + intrinsicHeight / 2)
                )
            } else {
                val scale: Float
                val width: Int
                val height: Int

                if (intrinsicWidth >= intrinsicHeight) {
                    scale = iconSize.toFloat() / intrinsicWidth.toFloat()
                    width = iconSize
                    height = (intrinsicHeight * scale).toInt()
                } else {
                    scale = iconSize.toFloat() / intrinsicHeight.toFloat()
                    height = iconSize
                    width = (intrinsicWidth * scale).toInt()
                }

                d.setBounds(
                    (w / 2 - width / 2),
                    (h / 2 - height / 2),
                    (w / 2 + width / 2),
                    (h / 2 + height / 2)
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                if (isAnimatedDrawable) (drawable as AnimatedVectorDrawable).apply {
                    this.start()
                }
            }
        }
        return super.onTouchEvent(event)
    }

}