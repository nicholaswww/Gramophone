package org.akanework.gramophone.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity

class LyricWidgetProvider : AppWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		val seekPi = PendingIntent.getBroadcast(
			context,
			GramophonePlaybackService.PENDING_INTENT_WIDGET_ID,
			Intent(context.packageName + ".SEEK_TO").setPackage(context.packageName),
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		)
		for (appWidgetId in appWidgetIds) {
			val views =
				RemoteViews(context.packageName, R.layout.lyric_widget).apply {
					setPendingIntentTemplate(R.id.list_view, seekPi)
					setRemoteAdapter(
						R.id.list_view,
						Intent(context, LyricWidgetService::class.java).apply<Intent> {
							this.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
							// Intents are compared using filterEquals() which ignores extras, so encode extras
							// in data to enforce comparison noticing the difference between different Intents.
							this.data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
						})
					setEmptyView(R.id.list_view, R.id.empty_view)
				}
			// setting null first fixes outdated data related bugs but causes flicker. oh well
			appWidgetManager.updateAppWidget(appWidgetId, null)
			appWidgetManager.updateAppWidget(appWidgetId, views)
		}
	}

	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle?
	) {
		onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
	}

	companion object {
		fun update(context: Context) {
			val awm = AppWidgetManager.getInstance(context)
			LyricWidgetProvider().onUpdate(context, awm, awm.appWidgetIds(context))
		}
		fun adapterUpdate(context: Context) {
			val awm = AppWidgetManager.getInstance(context)
			for (appWidgetId in awm.appWidgetIds(context)) {
				awm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view)
			}
		}
		fun hasWidget(context: Context): Boolean {
			val awm = AppWidgetManager.getInstance(context)
			return awm.getAppWidgetIds(ComponentName(context,
				LyricWidgetProvider::class.java)).isNotEmpty()
		}
		private fun AppWidgetManager.appWidgetIds(context: Context) =
			getAppWidgetIds(ComponentName(context, LyricWidgetProvider::class.java))
	}
}

class LyricWidgetService : RemoteViewsService() {
	override fun onGetViewFactory(intent: Intent): RemoteViewsFactory? {
		if (!intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
			throw IllegalStateException("where is EXTRA_APPWIDGET_ID?")
		/*val size = if (intent.hasExtra("width") || intent.hasExtra("height")) {
			if (!intent.hasExtra("width"))
				throw IllegalStateException("where is width? we have height")
			if (!intent.hasExtra("height"))
				throw IllegalStateException("where is height? we have width")
			SizeF(intent.getFloatExtra("width", -1f), intent.getFloatExtra("height", -1f))
		} else null*/
		return LyricRemoteViewsFactory(this,
			intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
	}
}

private class LyricRemoteViewsFactory(private val context: Context, private val appWidgetId: Int)
	: RemoteViewsService.RemoteViewsFactory {
	override fun onCreate() {
		// do nothing
	}

	override fun onDataSetChanged() {
		Handler(Looper.getMainLooper()).postDelayed({
			val li = GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.getCurrentLyricIndex()
			val awm = AppWidgetManager.getInstance(context)
			if (li != null) {
				awm.partiallyUpdateAppWidget(appWidgetId,
					RemoteViews(context.packageName, R.layout.lyric_widget).apply {
						setScrollPosition(R.id.list_view, li)
					}
				)
			}
		}, 100)
	}

	override fun onDestroy() {
		// do nothing
	}

	override fun getCount(): Int {
		return GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics?.unsyncedText?.size
			?: GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.size ?: 0
	}

	private val themeContext = ContextThemeWrapper(context, R.style.Theme_Gramophone)
	private val span = ForegroundColorSpan(ContextCompat.getColor(themeContext, R.color.sl_lyric_active))

	override fun getViewAt(position: Int): RemoteViews? {
		val cPos = runBlocking {
			withContext(Dispatchers.Main) {
				GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.controller?.contentPosition
			}
		}
		val item =
			(GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)?.text?.getOrNull(
				position
			)
		val itemUnsynced =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics?.unsyncedText?.getOrNull(
				position
			)
		val itemLegacy =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.getOrNull(
				position
			)
		if (item == null && itemUnsynced == null && itemLegacy == null) return null
		val isTranslation = item?.isTranslated ?: itemLegacy?.isTranslation ?: false
		val isBackground = item?.lyric?.speaker == SpeakerEntity.Background
		val isVoice2 = item?.lyric?.speaker == SpeakerEntity.Voice2
		val startTs = item?.lyric?.start?.toLong() ?: itemLegacy?.timeStamp ?: -1L
		val endTs = item?.lyric?.words?.lastOrNull()?.timeRange?.last?.toLong()
			?: (GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)?.text?.getOrNull(
				position + 1
			)?.lyric?.start?.toLong()
			?: GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.getOrNull(
				position + 1
			)?.timeStamp
			?: Long.MAX_VALUE
		val isActive = startTs == -1L || cPos != null && cPos >= startTs && cPos <= endTs
		return RemoteViews(
			context.packageName, when {
				isVoice2 && isTranslation -> R.layout.lyric_widget_txt_tlri
				isVoice2 -> R.layout.lyric_widget_txt_nnri
				isTranslation && isBackground -> R.layout.lyric_widget_txt_tbli
				isTranslation -> R.layout.lyric_widget_txt_tlli
				isBackground -> R.layout.lyric_widget_txt_bgli
				else -> R.layout.lyric_widget_txt_nnli
			}
		).apply {
			val sb = SpannableString(item?.lyric?.text ?: itemUnsynced ?: itemLegacy!!.content)
			if (isActive) {
				val hlChar = item?.lyric?.words?.findLast { it.timeRange.start <= cPos!!.toULong() }
					?.charRange?.last?.plus(1) ?: sb.length
				sb.setSpan(span, 0, hlChar, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
			}
			setTextViewText(R.id.lyric_widget_item, sb)
			if (startTs >= 0L)
				setOnClickFillInIntent(R.id.lyric_widget_item, Intent().apply {
					putExtras(Bundle().apply {
						putLong("seekTo", startTs)
					})
				})
		}
		return null
	}

	override fun getLoadingView(): RemoteViews? {
		return null
	}

	override fun getViewTypeCount(): Int {
		return 6
	}

	override fun getItemId(position: Int): Long {
		val item =
			(GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)?.text?.getOrNull(
				position
			)
		val itemUnsynced =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics?.unsyncedText?.getOrNull(
				position
			)
		val itemLegacy =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.getOrNull(position)
		if (item == null && itemUnsynced == null && itemLegacy == null) return 0L
		return ((item?.lyric?.text ?: itemUnsynced ?: itemLegacy!!.content).hashCode()).toLong()
			.shl(32) or
				(item?.lyric?.start?.hashCode() ?: itemLegacy?.timeStamp?.hashCode() ?: -1L).toLong()
	}

	override fun hasStableIds(): Boolean {
		return true
	}
}