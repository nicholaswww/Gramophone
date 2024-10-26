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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity

// TODO test on a5
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
			val views = RemoteViews(context.packageName, R.layout.lyric_widget)
			views.setPendingIntentTemplate(R.id.list_view, seekPi)
			views.setRemoteAdapter(
				R.id.list_view,
				Intent(context, LyricWidgetService::class.java).apply {
					putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
					data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
				})
			views.setEmptyView(R.id.list_view, R.id.empty_view)
			appWidgetManager.updateAppWidget(appWidgetId, views)
			appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view)
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
		Handler(Looper.getMainLooper()).post {
			val views = RemoteViews(context.packageName, R.layout.lyric_widget)
			val awm = AppWidgetManager.getInstance(context)
			val li = GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.getCurrentLyricIndex()
			if (li != null) {
				views.setScrollPosition(R.id.list_view, li + 2)
				awm.partiallyUpdateAppWidget(appWidgetId, views)
				views.setScrollPosition(R.id.list_view, li)
				awm.partiallyUpdateAppWidget(appWidgetId, views)
			}
		}
	}

	override fun onDestroy() {
		// do nothing
	}

	override fun getCount(): Int {
		return (GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)
			?.text?.size
			?: GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.size ?: 0
	}

	private val span = ForegroundColorSpan(ContextCompat.getColor(context, R.color.sl_lyric_active)) // TODO why is it pink?

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
		val itemLegacy =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.getOrNull(
				position
			)
		if (item == null && itemLegacy == null) return null
		val isTranslation = item?.isTranslated ?: itemLegacy!!.isTranslation
		val isBackground = item?.lyric?.speaker == SpeakerEntity.Background
		val isVoice2 = item?.lyric?.speaker == SpeakerEntity.Voice2
		val startTs = item?.lyric?.start?.toLong() ?: itemLegacy!!.timeStamp ?: 0L
		val endTs = item?.lyric?.words?.lastOrNull()?.timeRange?.last?.toLong()
			?: (GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)?.text?.getOrNull(
				position + 1
			)?.lyric?.start?.toLong()
			?: GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.getOrNull(
				position + 1
			)?.timeStamp
			?: Long.MAX_VALUE
		val isActive = cPos != null && cPos >= startTs && cPos <= endTs
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
			val sb = SpannableString(item?.lyric?.text ?: itemLegacy!!.content)
			if (isActive) {
				val hlChar = item?.lyric?.words?.findLast { it.timeRange.start <= cPos.toULong() }
					?.charRange?.last?.plus(1) ?: sb.length
				sb.setSpan(span, 0, hlChar, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
			}
			setTextViewText(R.id.lyric_widget_item, sb)
			setOnClickFillInIntent(R.id.lyric_widget_item, Intent().apply {
				putExtras(Bundle().apply {
					putLong("seekTo", startTs)
				})
			})
		}
		return null
	}

	override fun getLoadingView(): RemoteViews? {
		return null // TODO
	}

	override fun getViewTypeCount(): Int {
		return 6
	}

	override fun getItemId(position: Int): Long {
		val item =
			(GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyrics as? SemanticLyrics.SyncedLyrics?)?.text?.get(
				position
			)
		val itemLegacy =
			GramophonePlaybackService.instanceForWidgetAndOnlyWidget?.lyricsLegacy?.get(position)
		return (item?.lyric?.text?.hashCode() ?: itemLegacy!!.content.hashCode()).toLong()
			.shl(32) or
				(item?.lyric?.start?.hashCode() ?: itemLegacy!!.timeStamp.hashCode()).toLong()
	}

	override fun hasStableIds(): Boolean {
		return true
	}
}