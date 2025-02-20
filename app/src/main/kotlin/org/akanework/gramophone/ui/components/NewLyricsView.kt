package org.akanework.gramophone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.OptIn
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.TypefaceCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.text.getSpans
import androidx.core.widget.NestedScrollView
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.toULong
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.spans.MyForegroundColorSpan
import org.akanework.gramophone.logic.ui.spans.MyGradientSpan
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity
import org.akanework.gramophone.ui.MainActivity

private const val TAG = "NewLyricsView"

class NewLyricsView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val smallSizeFactor = 0.97f
    private val lyricAnimTime = 650f
    private var currentScrollTarget: Int? = null

    // TODO maybe reduce this to avoid really fast word skipping
    private val scaleInAnimTime = lyricAnimTime / 2f
    private val isElegantTextHeight = false
    private val scaleColorInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var typeface: Typeface
    private val grdWidth = context.resources.getDimension(R.dimen.lyric_gradient_size)
    private val defaultTextSize = context.resources.getDimension(R.dimen.lyric_text_size)
    private val translationTextSize = context.resources.getDimension(R.dimen.lyric_tl_text_size)
    private val translationBackgroundTextSize =
        context.resources.getDimension(R.dimen.lyric_tl_bg_text_size)
    private var colorSpanPool = mutableListOf<MyForegroundColorSpan>()
    private var spForRender: List<SbItem>? = null
    private var spForMeasure: Pair<Pair<Int, Int>, List<SbItem>>? = null
    private var lyrics: SemanticLyrics? = null
    private var posForRender = 0uL
    private val activity
        get() = context as MainActivity
    private val instance
        get() = activity.getPlayer()
    private val scrollView // TODO autoscroll
        get() = parent as NestedScrollView

    // -/M/F/D insanity starts here
    private var defaultTextColor = 0
    private var highlightTextColor = 0
    private var defaultTextColorM = 0
    private var highlightTextColorM = 0
    private var defaultTextColorF = 0
    private var highlightTextColorF = 0
    private var defaultTextColorD = 0
    private var highlightTextColorD = 0
    private val defaultTextPaint = TextPaint().apply {
        color = Color.RED
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
        textSize = defaultTextSize
    }
    private val translationTextPaint = TextPaint().apply {
        color = Color.GREEN
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
        textSize = translationTextSize
    }
    private val translationBackgroundTextPaint = TextPaint().apply {
        color = Color.BLUE
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
        textSize = translationBackgroundTextSize
    }
    private val defaultTextPaintM = lazy {
        TextPaint().apply {
            color = defaultTextColorM
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = defaultTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private val translationTextPaintM = lazy {
        TextPaint().apply {
            color = defaultTextColorM
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = translationTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private val defaultTextPaintF = lazy {
        TextPaint().apply {
            color = defaultTextColorF
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = defaultTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private val translationTextPaintF = lazy {
        TextPaint().apply {
            color = defaultTextColorF
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = translationTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private val defaultTextPaintD = lazy {
        TextPaint().apply {
            color = defaultTextColorD
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = defaultTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private val translationTextPaintD = lazy {
        TextPaint().apply {
            color = defaultTextColorD
            isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
            textSize = translationTextSize; typeface = this@NewLyricsView.typeface
        }
    }
    private var wordActiveSpan = MyForegroundColorSpan(Color.CYAN)
    private var wordActiveSpanM = lazy { MyForegroundColorSpan(highlightTextColorM) }
    private var wordActiveSpanF = lazy { MyForegroundColorSpan(highlightTextColorF) }
    private var wordActiveSpanD = lazy { MyForegroundColorSpan(highlightTextColorD) }
    private var gradientSpanPool = mutableListOf<MyGradientSpan>()
    private var gradientSpanPoolM = lazy { mutableListOf<MyGradientSpan>() }
    private var gradientSpanPoolF = lazy { mutableListOf<MyGradientSpan>() }
    private var gradientSpanPoolD = lazy { mutableListOf<MyGradientSpan>() }
    private fun makeGradientSpan() = MyGradientSpan(grdWidth, defaultTextColor, highlightTextColor)
    private fun makeGradientSpanM() =
        MyGradientSpan(grdWidth, defaultTextColorM, highlightTextColorM)

    private fun makeGradientSpanF() =
        MyGradientSpan(grdWidth, defaultTextColorF, highlightTextColorF)

    private fun makeGradientSpanD() =
        MyGradientSpan(grdWidth, defaultTextColorD, highlightTextColorD)

    init {
        applyTypefaces()
    }

    fun updateTextColor(
        newColor: Int, newHighlightColor: Int, newColorM: Int,
        newHighlightColorM: Int, newColorF: Int, newHighlightColorF: Int,
        newColorD: Int, newHighlightColorD: Int
    ) {
        var changed = false
        var changedM = false
        var changedF = false
        var changedD = false
        if (defaultTextColor != newColor) {
            defaultTextColor = newColor
            defaultTextPaint.color = defaultTextColor
            translationTextPaint.color = defaultTextColor
            translationBackgroundTextPaint.color = defaultTextColor
            changed = true
        }
        if (defaultTextColorM != newColorM) {
            defaultTextColorM = newColorM
            if (defaultTextPaintM.isInitialized()) {
                defaultTextPaintM.value.color = defaultTextColorM
                changedM = true
            }
            if (translationTextPaintM.isInitialized()) {
                translationTextPaintM.value.color = defaultTextColorM
                changedM = true
            }
        }
        if (defaultTextColorF != newColorF) {
            defaultTextColorF = newColorF
            if (defaultTextPaintF.isInitialized()) {
                defaultTextPaintF.value.color = defaultTextColorF
                changedF = true
            }
            if (translationTextPaintF.isInitialized()) {
                translationTextPaintF.value.color = defaultTextColorF
                changedF = true
            }
        }
        if (defaultTextColorD != newColorD) {
            defaultTextColorD = newColorD
            if (defaultTextPaintD.isInitialized()) {
                defaultTextPaintD.value.color = defaultTextColorD
                changedD = true
            }
            if (translationTextPaintD.isInitialized()) {
                translationTextPaintD.value.color = defaultTextColorD
                changedD = true
            }
        }
        if (highlightTextColor != newHighlightColor) {
            highlightTextColor = newHighlightColor
            wordActiveSpan.color = highlightTextColor
            changed = true
        }
        if (highlightTextColorM != newHighlightColorM) {
            highlightTextColorM = newHighlightColorM
            if (wordActiveSpanM.isInitialized()) {
                wordActiveSpanM.value.color = highlightTextColorM
                changedM = true
            }
        }
        if (highlightTextColorF != newHighlightColorF) {
            highlightTextColorF = newHighlightColorF
            if (wordActiveSpanF.isInitialized()) {
                wordActiveSpanF.value.color = highlightTextColorF
                changedF = true
            }
        }
        if (highlightTextColorD != newHighlightColorD) {
            highlightTextColorD = newHighlightColorD
            if (wordActiveSpanD.isInitialized()) {
                wordActiveSpanD.value.color = highlightTextColorD
                changedD = true
            }
        }
        if (changed) {
            gradientSpanPool.clear()
            (1..3).forEach { gradientSpanPool.add(makeGradientSpan()) }
        }
        if (changedM && gradientSpanPoolM.isInitialized()) {
            gradientSpanPoolM.value.clear()
            (1..3).forEach { gradientSpanPoolM.value.add(makeGradientSpanM()) }
        }
        if (changedF && gradientSpanPoolF.isInitialized()) {
            gradientSpanPoolF.value.clear()
            (1..3).forEach { gradientSpanPoolF.value.add(makeGradientSpanF()) }
        }
        if (changedD && gradientSpanPoolD.isInitialized()) {
            gradientSpanPoolD.value.clear()
            (1..3).forEach { gradientSpanPoolD.value.add(makeGradientSpanD()) }
        }
        if (changed || changedM || changedF || changedD) {
            spForRender?.forEach {
                it.text.getSpans<MyGradientSpan>()
                    .forEach { s -> it.text.removeSpan(s) }
            }
            invalidate()
        }
    }

    fun updateLyrics(parsedLyrics: SemanticLyrics?) {
        spForRender = null
        spForMeasure = null
        requestLayout()
        lyrics = parsedLyrics
    }

    fun updateLyricPositionFromPlaybackPos() {
        if (getCurrentPosition() != posForRender) // if not playing, might stay same
            invalidate()
    }

    // https://github.com/androidx/media/issues/1578
    @OptIn(UnstableApi::class)
    private fun getCurrentPosition() = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        ?.endedWorkaroundPlayer?.currentPosition?.toULong()
        ?: instance?.currentPosition?.toULong() ?: 0uL

    fun onPrefsChanged() {
        applyTypefaces()
        spForRender = null
        spForMeasure = null
        requestLayout()
    }

    private fun applyTypefaces() {
        typeface = if (prefs.getBooleanStrict("lyric_bold", false)) {
            TypefaceCompat.create(context, null, 700, false)
        } else {
            TypefaceCompat.create(context, null, 500, false)
        }
        defaultTextPaint.typeface = typeface
        translationTextPaint.typeface = typeface
        translationBackgroundTextPaint.typeface = typeface
        if (defaultTextPaintM.isInitialized())
            defaultTextPaintM.value.typeface = typeface
        if (translationTextPaintM.isInitialized())
            translationTextPaintM.value.typeface = typeface
        if (defaultTextPaintF.isInitialized())
            defaultTextPaintF.value.typeface = typeface
        if (translationTextPaintF.isInitialized())
            translationTextPaintF.value.typeface = typeface
        if (defaultTextPaintD.isInitialized())
            defaultTextPaintD.value.typeface = typeface
        if (translationTextPaintD.isInitialized())
            translationTextPaintD.value.typeface = typeface
    }

    override fun onDraw(canvas: Canvas) {
        posForRender = getCurrentPosition().also {
            if (posForRender > it && posForRender - it < 1000uL)
                Log.w(TAG, "regressing position by ${posForRender - it}ms from $posForRender to $it!")
        }
        if (spForRender == null) {
            requestLayout()
            return
        }
        var animating = false
        var heightSoFar = 0
        var firstHighlight: Int? = null
        canvas.save()
        val lines = if (lyrics is SemanticLyrics.SyncedLyrics)
            (lyrics as SemanticLyrics.SyncedLyrics).text else null
        spForRender!!.forEachIndexed { i, it ->
            var spanEnd = -1
            var spanStartGradient = -1
            var realGradientStart = -1
            var realGradientEnd = -1
            var wordIdx: Int? = null
            var gradientProgress = Float.NEGATIVE_INFINITY
            val firstTs = lines?.get(i)?.start ?: ULong.MIN_VALUE
            val lastTs = lines?.get(i)?.end ?: ULong.MAX_VALUE
            val timeOffsetForUse = min(
                scaleInAnimTime, min(
                    lerp(
                        firstTs.toFloat(), lastTs.toFloat(),
                        0.5f
                    ) - firstTs.toFloat(), firstTs.toFloat()
                )
            )
            val highlight = posForRender >= firstTs - timeOffsetForUse.toULong() &&
                    posForRender <= lastTs + timeOffsetForUse.toULong()
            val scaleInProgress = if (lines == null) 1f else lerpInv(
                firstTs.toFloat() -
                        timeOffsetForUse,
                firstTs.toFloat() + timeOffsetForUse,
                posForRender.toFloat()
            )
            val scaleOutProgress = if (lines == null) 1f else lerpInv(
                lastTs.toFloat() -
                        timeOffsetForUse,
                lastTs.toFloat() + timeOffsetForUse,
                posForRender.toFloat()
            )
            val hlScaleFactor = if (lines == null) smallSizeFactor else {
                // lerp() argument order is swapped because we divide by this factor
                if (scaleOutProgress >= 0f && scaleOutProgress <= 1f)
                    lerp(
                        smallSizeFactor,
                        1f,
                        scaleColorInterpolator.getInterpolation(scaleOutProgress)
                    )
                else if (scaleInProgress >= 0f && scaleInProgress <= 1f)
                    lerp(
                        1f,
                        smallSizeFactor,
                        scaleColorInterpolator.getInterpolation(scaleInProgress)
                    )
                else if (highlight)
                    smallSizeFactor
                else 1f
            }
            val isRtl = it.layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            val alignmentNormal = if (isRtl) it.layout.alignment == Layout.Alignment.ALIGN_OPPOSITE
            else it.layout.alignment == Layout.Alignment.ALIGN_NORMAL
            if ((scaleInProgress >= -.1f && scaleInProgress <= 1f) ||
                (scaleOutProgress >= -.1f && scaleOutProgress <= 1f)
            )
                animating = true
            if (highlight && firstHighlight == null)
                firstHighlight = heightSoFar
            heightSoFar += (it.paddingTop.toFloat() / hlScaleFactor).also {
                canvas.translate(
                    0f,
                    it
                )
            }.toInt()
            if (highlight) {
                canvas.save()
                canvas.scale(1f / hlScaleFactor, 1f / hlScaleFactor)
                if (lines != null && lines[i].words != null) {
                    wordIdx =
                        lines[i].words?.indexOfLast { it.timeRange.start <= posForRender }
                    if (wordIdx == -1) wordIdx = null
                    if (wordIdx != null) {
                        val word = lines[i].words!![wordIdx]
                        spanEnd = word.charRange.last + 1 // get exclusive end
                        val gradientEndTime = min(
                            lastTs.toFloat() - timeOffsetForUse,
                            word.timeRange.last.toFloat()
                        )
                        val gradientStartTime = min(
                            max(
                                word.timeRange.start.toFloat(),
                                firstTs.toFloat() - timeOffsetForUse
                            ), gradientEndTime - 1f
                        )
                        gradientProgress = lerpInv(
                            gradientStartTime, gradientEndTime,
                            posForRender.toFloat()
                        )
                        if (gradientProgress >= 0f && gradientProgress <= 1f) {
                            spanStartGradient = word.charRange.first
                            // be greedy and eat as much as the line as can be eaten (text that is
                            // same line + is in same text direction). improves font rendering for
                            // japanese if font rendering renders whole text in one pass
                            val wordStartLine = it.layout.getLineForOffset(word.charRange.first)
                            val wordEndLine =
                                it.layout.getLineForOffset(word.charRange.endInclusive)
                            val firstCharOnStartLine = it.layout.getLineStart(wordStartLine)
                            val lastCharOnEndLineExcl = it.layout.getLineEnd(wordEndLine)
                            realGradientStart = lines[i].words?.lastOrNull {
                                it.charRange.first >= firstCharOnStartLine && it.charRange.last <
                                        word.charRange.first && it.isRtl != word.isRtl
                            }?.charRange?.last?.plus(1) ?: firstCharOnStartLine
                            realGradientEnd = lines[i].words?.firstOrNull {
                                it.charRange.first > word.charRange.last && it.charRange.last <
                                        lastCharOnEndLineExcl && it.isRtl != word.isRtl
                            }?.charRange?.first ?: lastCharOnEndLineExcl
                        }
                    }
                } else {
                    spanEnd = it.text.length
                }
            }
            if (!alignmentNormal) {
                if (!highlight)
                    canvas.save()
                if (it.layout.alignment != Layout.Alignment.ALIGN_CENTER)
                    canvas.translate(width * (1 - smallSizeFactor / hlScaleFactor), 0f)
                else // Layout.Alignment.ALIGN_CENTER
                    canvas.translate(width * ((1 - smallSizeFactor / hlScaleFactor) / 2), 0f)
            }
            if (gradientProgress >= -.1f && gradientProgress <= 1f)
                animating = true
            val spanEndWithoutGradient = if (realGradientStart == -1) spanEnd else realGradientStart
            val inColorAnim = (scaleInProgress >= 0f && scaleInProgress <= 1f && gradientProgress ==
                    Float.NEGATIVE_INFINITY) || (scaleOutProgress >= 0f && scaleOutProgress <= 1f)
            var colorSpan = it.text.getSpans<MyForegroundColorSpan>().firstOrNull()
            val cachedEnd = colorSpan?.let { j -> it.text.getSpanEnd(j) } ?: -1
            val defaultColorForLine = when (it.speaker) {
                SpeakerEntity.Male -> defaultTextColorM
                SpeakerEntity.Female -> defaultTextColorF
                SpeakerEntity.Duet -> defaultTextColorD
                else -> defaultTextColor
            }
            val highlightColorForLine = when (it.speaker) {
                SpeakerEntity.Male -> highlightTextColorM
                SpeakerEntity.Female -> highlightTextColorF
                SpeakerEntity.Duet -> highlightTextColorD
                else -> highlightTextColor
            }
            val wordActiveSpanForLine = when (it.speaker) {
                SpeakerEntity.Male -> wordActiveSpanM.value
                SpeakerEntity.Female -> wordActiveSpanF.value
                SpeakerEntity.Duet -> wordActiveSpanD.value
                else -> wordActiveSpan
            }
            val gradientSpanPoolForLine = when (it.speaker) {
                SpeakerEntity.Male -> gradientSpanPoolM.value
                SpeakerEntity.Female -> gradientSpanPoolF.value
                SpeakerEntity.Duet -> gradientSpanPoolD.value
                else -> gradientSpanPool
            }
            val col = if (inColorAnim) ColorUtils.blendARGB(
                if (scaleOutProgress >= 0f &&
                    scaleOutProgress <= 1f
                ) highlightColorForLine else defaultColorForLine, if (
                    scaleInProgress >= 0f && scaleInProgress <= 1f && gradientProgress == Float
                        .NEGATIVE_INFINITY
                ) highlightColorForLine else defaultColorForLine,
                scaleColorInterpolator.getInterpolation(
                    if (scaleOutProgress >= 0f &&
                        scaleOutProgress <= 1f
                    ) scaleOutProgress else scaleInProgress
                )
            ) else 0
            if (cachedEnd != spanEndWithoutGradient || inColorAnim != (colorSpan != wordActiveSpanForLine)) {
                if (cachedEnd != -1) {
                    it.text.removeSpan(colorSpan!!)
                    if (colorSpan != wordActiveSpanForLine && (!inColorAnim || spanEndWithoutGradient == -1)) {
                        if (colorSpanPool.size < 10)
                            colorSpanPool.add(colorSpan)
                        colorSpan = null
                    } else if (inColorAnim && colorSpan == wordActiveSpanForLine)
                        colorSpan = null
                }
                if (spanEndWithoutGradient != -1) {
                    if (inColorAnim && colorSpan == null)
                        colorSpan = colorSpanPool.getOrElse(0) { MyForegroundColorSpan(col) }
                    else if (!inColorAnim)
                        colorSpan = wordActiveSpanForLine
                    it.text.setSpan(
                        colorSpan, 0, spanEndWithoutGradient,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
            }
            if (inColorAnim && spanEndWithoutGradient != -1) {
                if (colorSpan!! == wordActiveSpanForLine)
                    throw IllegalStateException("colorSpan == wordActiveSpan")
                colorSpan.color = col
            }
            var gradientSpan = it.text.getSpans<MyGradientSpan>().firstOrNull()
            val gradientSpanStart = gradientSpan?.let { j -> it.text.getSpanStart(j) } ?: -1
            val gradientSpanEnd = gradientSpan?.let { j -> it.text.getSpanEnd(j) } ?: -1
            if (gradientSpanStart != realGradientStart || gradientSpanEnd != realGradientEnd) {
                if (gradientSpanStart != -1) {
                    it.text.removeSpan(gradientSpan!!)
                    if (realGradientStart == -1) {
                        if (gradientSpanPoolForLine.size < 10)
                            gradientSpanPoolForLine.add(gradientSpan)
                        gradientSpan = null
                    }
                }
                if (realGradientStart != -1) {
                    if (gradientSpan == null)
                        gradientSpan = gradientSpanPoolForLine.getOrElse(0) {
                            when (lines?.get(i)?.speaker) {
                                SpeakerEntity.Male -> makeGradientSpanM()
                                SpeakerEntity.Female -> makeGradientSpanF()
                                SpeakerEntity.Duet -> makeGradientSpanD()
                                else -> makeGradientSpan()
                            }
                        }
                    it.text.setSpan(
                        gradientSpan, realGradientStart, realGradientEnd,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
            }
            if (gradientSpan != null) {
                gradientSpan.runCount = 0
                gradientSpan.lastLineCount = -1
                gradientSpan.lineOffsets = it.words!![wordIdx!!]
                gradientSpan.totalCharsForProgress = spanEnd - spanStartGradient
                // We get called once per run + one additional time per run if run direction isn't
                // same as paragraph direction.
                gradientSpan.runToLineMappings = it.rlm!!
                gradientSpan.progress = gradientProgress
            }
            it.layout.draw(canvas)
            if (highlight || !alignmentNormal)
                canvas.restore()
            heightSoFar += ((it.layout.height.toFloat() + it.paddingBottom) / hlScaleFactor)
                .also { canvas.translate(0f, it) }.toInt()
        }
        canvas.restore()
        if (animating)
            invalidate()
        if (firstHighlight != null && firstHighlight != currentScrollTarget)
            scrollView.smoothScrollTo(0, firstHighlight, lyricAnimTime.toInt())
        currentScrollTarget = firstHighlight
    }

    @SuppressLint("ClickableViewAccessibility") // TODO https://developer.android.com/guide/topics/ui/accessibility/custom-views :(
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (spForRender == null) {
            requestLayout()
        } else if (event?.action == MotionEvent.ACTION_UP) {
            val y = event.y
            var heightSoFar = 0
            var foundItem = -1
            val lines = (lyrics as? SemanticLyrics.SyncedLyrics)?.text
            if (lines != null) {
                spForRender!!.forEachIndexed { i, it ->
                    val firstTs = lines[i].start
                    val lastTs = lines[i].words?.lastOrNull()?.timeRange?.last ?: lines
                        .find { it.start > lines[i].start }?.start?.minus(1uL)
                    ?: Long.MAX_VALUE.toULong()
                    val timeOffsetForUse = min(scaleInAnimTime, min(lerp(firstTs.toFloat(),
                        lastTs.toFloat(), 0.5f) - firstTs.toFloat(), firstTs.toFloat()))
                    val highlight = posForRender >= firstTs - timeOffsetForUse.toULong() &&
                            posForRender <= lastTs + timeOffsetForUse.toULong()
                    val scaleInProgress = lerpInv(
                        firstTs.toFloat() - timeOffsetForUse,
                        firstTs.toFloat() + timeOffsetForUse, posForRender.toFloat()
                    )
                    val scaleOutProgress = lerpInv(
                        lastTs.toFloat() - timeOffsetForUse,
                        lastTs.toFloat() + timeOffsetForUse, posForRender.toFloat()
                    )
                    val hlScaleFactor =
                        // lerp() argument order is swapped because we divide by this factor
                        if (scaleOutProgress >= 0f && scaleOutProgress <= 1f)
                            lerp(
                                smallSizeFactor, 1f,
                                scaleColorInterpolator.getInterpolation(scaleOutProgress)
                            )
                        else if (scaleInProgress >= 0f && scaleInProgress <= 1f)
                            lerp(
                                1f, smallSizeFactor,
                                scaleColorInterpolator.getInterpolation(scaleInProgress)
                            )
                        else if (highlight)
                            smallSizeFactor
                        else 1f
                    val myHeight =
                        (it.paddingTop + it.layout.height + it.paddingBottom) / hlScaleFactor
                    if (y >= heightSoFar && y <= heightSoFar + myHeight && lines[i].isClickable)
                        foundItem = i
                    heightSoFar += myHeight.toInt()
                }
            }
            if (foundItem != -1) {
                instance?.seekTo(lines!![foundItem].start.toLong())
                performClick()
            }
            return true
        } else if (event?.action == MotionEvent.ACTION_DOWN) {
            return true // this is needed so that we get the UP event we care about
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val myWidth = getDefaultSize(minimumWidth, widthMeasureSpec)
        if (spForMeasure == null || spForMeasure!!.first.first != myWidth)
            spForMeasure = buildSpForMeasure(lyrics, myWidth)
        setMeasuredDimension(
            myWidth,
            getDefaultSize(spForMeasure!!.first.second, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (spForMeasure == null || spForMeasure!!.first.first != right - left
            || spForMeasure!!.first.second != bottom - top
        )
            spForMeasure = buildSpForMeasure(lyrics, right - left)
        spForRender = spForMeasure!!.second
        invalidate()
    }

    fun buildSpForMeasure(lyrics: SemanticLyrics?, width: Int): Pair<Pair<Int, Int>, List<SbItem>> {
        val lines = lyrics?.unsyncedText ?: listOf(context.getString(R.string.no_lyric_found) to null)
        val syncedLines = (lyrics as? SemanticLyrics.SyncedLyrics?)?.text
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            || !prefs.getBooleanStrict("pixel_perfect_measurement_legacy", false)) null else
            createBitmap(1000, 1000) // TODO
        val pixels = b?.let { IntArray(it.width * it.height) }
        val c = b?.let { Canvas(it) }
        val tmpPaint = TextPaint()
        val spLines = lines.mapIndexed { i, it ->
            val sb = SpannableStringBuilder(it.first)
            val speaker = syncedLines?.get(i)?.speaker ?: it.second
            val align = if (prefs.getBooleanStrict("lyric_center", false))
                Layout.Alignment.ALIGN_CENTER
            else if (syncedLines != null && speaker?.isVoice2 == true)
                Layout.Alignment.ALIGN_OPPOSITE
            else Layout.Alignment.ALIGN_NORMAL
            val tl = syncedLines != null && syncedLines[i].isTranslated
            val bg = speaker?.isBackground == true
            val paddingTop = if (syncedLines?.get(i)?.isTranslated == true) 2 else 18
            val paddingBottom = if (i + 1 < (syncedLines?.size ?: -1) &&
                syncedLines?.get(i + 1)?.isTranslated == true
            ) 2 else 18
            val layout = StaticLayoutBuilderCompat.obtain(
                sb, when {
                    speaker == SpeakerEntity.Male && tl -> translationTextPaintM.value
                    speaker == SpeakerEntity.Male -> defaultTextPaintM.value
                    speaker == SpeakerEntity.Female && tl -> translationTextPaintF.value
                    speaker == SpeakerEntity.Female -> defaultTextPaintF.value
                    speaker == SpeakerEntity.Duet && tl -> translationTextPaintD.value
                    speaker == SpeakerEntity.Duet -> defaultTextPaintD.value
                    tl && bg -> translationBackgroundTextPaint
                    tl || bg -> translationTextPaint
                    else -> defaultTextPaint
                }, (width * smallSizeFactor).toInt()
            ).setAlignment(align).build()
            val paragraphRtl = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            val alignmentNormal = if (paragraphRtl) align == Layout.Alignment.ALIGN_OPPOSITE
            else align == Layout.Alignment.ALIGN_NORMAL
            val lineOffsets = syncedLines?.get(i)?.words?.map {
                val ia = mutableListOf<Int>()
                val firstLine = layout.getLineForOffset(it.charRange.first)
                val lastLine = layout.getLineForOffset(it.charRange.last + 1)
                for (line in firstLine..lastLine) {
                    val firstInLine = max(it.charRange.first, layout.getLineStart(line))
                    val lastInLineExcl = min(it.charRange.last + 1, layout.getLineEnd(line))
                    var horizontalStart = if (paragraphRtl == it.isRtl)
                        layout.getPrimaryHorizontal(firstInLine)
                    else layout.getSecondaryHorizontal(firstInLine)
                    tmpPaint.set(layout.paint)
                    tmpPaint.color = Color.RED
                    tmpPaint.bgColor = Color.TRANSPARENT
                    // Use StaticLayout instead of Paint.measureText() for V+ useBoundsForWidth
                    // TODO do we have to? or is measureText() actually sufficient?
                    val l = StaticLayoutBuilderCompat
                        .obtain(layout.text, tmpPaint, Int.MAX_VALUE)
                        .setAlignment(if (it.isRtl) Layout.Alignment.ALIGN_OPPOSITE
                        else Layout.Alignment.ALIGN_NORMAL)
                        .setIsRtl(it.isRtl)
                        .setStart(firstInLine)
                        .setEnd(lastInLineExcl)
                        .build()
                    val w = l.getLineRight(0) - l.getLineLeft(0) + if (b != null) {
                        // this is a very dumb solution and hence disabled by default
                        b.eraseColor(Color.TRANSPARENT)
                        // Best effort: assume that overhang is not larger than 50px
                        // 'cause that would be a lot. We have no way to retrieve this information
                        c!!.withTranslation(50f, 50f) {
                            l.draw(this)
                        }
                        val stride = b.width
                        b.getPixels(pixels!!, 0, stride, 0, 0, b.width, b.height)
                        var minX: Int? = null
                        out@for (x in 0..<b.width) {
                            for (y in 0..<b.height) {
                                if ((pixels[y*stride+x] shr 24) and 0xff != 0) {
                                    minX = x
                                    break@out
                                }
                            }
                        }
                        if (minX != null) minX -= 50 else minX = -1
                        if (minX < 0) minX * -1 else 0
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        // just add a few pixels on top if RTL as approximation of the above :D
                        if (it.isRtl) 5 else 0
                    } else 0
                    val horizontalEnd = horizontalStart + w * if (it.isRtl) -1 else 1
                    val horizontalLeft = min(horizontalStart, horizontalEnd)
                    val horizontalRight = max(horizontalStart, horizontalEnd)
                    ia.add(horizontalLeft.toInt()) // offset from left to start of word
                    ia.add((horizontalRight - horizontalLeft).roundToInt()) // width of text in this line
                    ia.add(firstInLine - it.charRange.first)
                    ia.add(lastInLineExcl - it.charRange.first)
                    ia.add(if (it.isRtl) -1 else 1)
                }
                return@map ia
            }
            SbItem(layout, sb, paddingTop.dpToPx(context), paddingBottom.dpToPx(context),
                lineOffsets, lineOffsets?.let { _ ->
                    (0..<layout.lineCount).map { line ->
                        LrcUtils.findBidirectionalBarriers(layout.text.subSequence(
                            layout.getLineStart(line), layout.getLineEnd(line))).flatMap {
                            if (it.second == alignmentNormal)
                                listOf(line, line)
                            else
                                listOf(line)
                        }
                    }.flatten()
                }, speaker)
        }
        val heights = spLines.map { it.layout.height + it.paddingTop + it.paddingBottom }
        return Pair(
            Pair(
                width,
                (heights.max() * (1 - (1 / smallSizeFactor)) + heights.sum()).toInt()
            ), spLines
        )
    }

    data class SbItem(
        val layout: StaticLayout, val text: SpannableStringBuilder,
        val paddingTop: Int, val paddingBottom: Int, val words: List<List<Int>>?,
        val rlm: List<Int>?, val speaker: SpeakerEntity?
    )

    // == start scroll ==
    /* TODO + maybe center current line
    private lateinit var edgeEffectTop: EdgeEffect
    private lateinit var edgeEffectBottom: EdgeEffect
    private lateinit var edgeEffectLeft: EdgeEffect
    private lateinit var edgeEffectRight: EdgeEffect

    private var edgeEffectTopActive: Boolean = false
    private var edgeEffectBottomActive: Boolean = false
    private var edgeEffectLeftActive: Boolean = false
    private var edgeEffectRightActive: Boolean = false

    override fun computeScroll() {
        super.computeScroll()

        var needsInvalidate = false

        // The scroller isn't finished, meaning a fling or
        // programmatic pan operation is active.
        if (scroller.computeScrollOffset()) {
            val surfaceSize: Point = computeScrollSurfaceSize()
            val currX: Int = scroller.currX
            val currY: Int = scroller.currY

            val (canScrollX: Boolean, canScrollY: Boolean) = currentViewport.run {
                (left > AXIS_X_MIN || right < AXIS_X_MAX) to (top > AXIS_Y_MIN || bottom < AXIS_Y_MAX)
            }

            /*
             * If you are zoomed in, currX or currY is
             * outside of bounds, and you aren't already
             * showing overscroll, then render the overscroll
             * glow edge effect.
             */
            if (canScrollX
                && currX < 0
                && edgeEffectLeft.isFinished
                && !edgeEffectLeftActive) {
                edgeEffectLeft.onAbsorb(scroller.currVelocity.toInt())
                edgeEffectLeftActive = true
                needsInvalidate = true
            } else if (canScrollX
                && currX > surfaceSize.x - contentRect.width()
                && edgeEffectRight.isFinished
                && !edgeEffectRightActive) {
                edgeEffectRight.onAbsorb(scroller.currVelocity.toInt())
                edgeEffectRightActive = true
                needsInvalidate = true
            }
        }
    }*/


}