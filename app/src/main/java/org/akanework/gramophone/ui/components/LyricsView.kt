package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.MainActivity

class LyricsView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
    SharedPreferences.OnSharedPreferenceChangeListener {


    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var recyclerView: MyRecyclerView? = null
    private var newView: NewLyricsView? = null
    private val adapter
        get() = recyclerView?.adapter as LegacyLyricsAdapter?
    private var defaultTextColor = 0
    private var highlightTextColor = 0
    private var defaultTextColorM = 0
    private var highlightTextColorM = 0
    private var defaultTextColorF = 0
    private var highlightTextColorF = 0
    private var defaultTextColorD = 0
    private var highlightTextColorD = 0
    private var lyrics: SemanticLyrics? = null

    init {
        createView()
    }

    private fun createView() {
        removeAllViews()
        val oldPaddingTop = newView?.paddingTop ?: recyclerView?.paddingTop ?: 0
        val oldPaddingBottom = newView?.paddingBottom ?: recyclerView?.paddingBottom ?: 0
        val oldPaddingLeft = newView?.paddingLeft ?: recyclerView?.paddingLeft ?: 0
        val oldPaddingRight = newView?.paddingRight ?: recyclerView?.paddingRight ?: 0
        recyclerView = null
        newView = null
        if (prefs.getBooleanStrict("lyric_ui", false)) {
            inflate(context, R.layout.lyric_view_v2, this)
            newView = findViewById(R.id.lyric_view)
            newView?.setPadding(oldPaddingLeft, oldPaddingTop, oldPaddingRight, oldPaddingBottom)
            newView?.instance = object : NewLyricsView.Callbacks {
                // TODO https://github.com/androidx/media/issues/1578
                @OptIn(UnstableApi::class)
                override fun getCurrentPosition(): ULong =
                    GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                        ?.endedWorkaroundPlayer?.currentPosition?.toULong()
                        ?: (context as MainActivity).getPlayer()?.currentPosition?.toULong() ?: 0uL

                override fun seekTo(position: ULong) {
                    (context as MainActivity).getPlayer()?.seekTo(position.toLong())
                }
            }
            newView?.updateTextColor(
                defaultTextColor, highlightTextColor, defaultTextColorM,
                highlightTextColorM, defaultTextColorF, highlightTextColorF, defaultTextColorD,
                highlightTextColorD
            )
            newView?.updateLyrics(lyrics)
        } else {
            inflate(context, R.layout.lyric_view, this)
            recyclerView = findViewById(R.id.recycler_view)
            recyclerView?.setPadding(oldPaddingLeft, oldPaddingTop, oldPaddingRight, oldPaddingBottom)
            recyclerView!!.adapter = LegacyLyricsAdapter(context).also {
                it.updateTextColor(defaultTextColor, highlightTextColor)
            }
            recyclerView!!.addItemDecoration(LyricPaddingDecoration(context))
            adapter?.updateLyrics(lyrics)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        adapter?.updateLyricStatus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if ((key == "lyric_center" || key == "lyric_bold") && adapter != null)
            adapter?.onPrefsChanged()
        else if (key == "lyric_center" || key == "lyric_bold" || key == "lyric_no_animation" ||
            key == "lyric_char_scaling" || key == "translation_auto_word")
            newView?.onPrefsChanged(key)
        else if (key == "lyric_ui")
            createView()
    }

    fun updateLyricPositionFromPlaybackPos() {
        adapter?.updateLyricPositionFromPlaybackPos()
        newView?.updateLyricPositionFromPlaybackPos()
    }

    fun updateLyrics(parsedLyrics: SemanticLyrics?) {
        lyrics = parsedLyrics
        adapter?.updateLyrics(lyrics)
        newView?.updateLyrics(lyrics)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        recyclerView?.setPadding(left, top, right, bottom)
        newView?.setPadding(left, top, right, bottom)
    }

    fun updateTextColor(
        newColor: Int, newHighlightColor: Int, newColorM: Int,
        newHighlightColorM: Int, newColorF: Int, newHighlightColorF: Int,
        newColorD: Int, newHighlightColorD: Int
    ) {
        defaultTextColor = newColor
        highlightTextColor = newHighlightColor
        defaultTextColorM = newColorM
        highlightTextColorM = newHighlightColorM
        defaultTextColorF = newColorF
        highlightTextColorF = newHighlightColorF
        defaultTextColorD = newColorD
        highlightTextColorD = newHighlightColorD
        adapter?.updateTextColor(defaultTextColor, highlightTextColor)
        newView?.updateTextColor(
            defaultTextColor, highlightTextColor, defaultTextColorM,
            highlightTextColorM, defaultTextColorF, highlightTextColorF, defaultTextColorD,
            highlightTextColorD
        )
    }
}
