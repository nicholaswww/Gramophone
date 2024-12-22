/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.adapters

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.CustomGridLayoutManager
import org.akanework.gramophone.ui.components.GridPaddingDecoration
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType
import uk.akane.libphonograph.items.Item

abstract class BaseAdapter<T>(
    protected val fragment: Fragment,
    liveData: Flow<List<T>>,
    sortHelper: Sorter.Helper<T>,
    naturalOrderHelper: Sorter.NaturalOrderHelper<T>?,
    initialSortType: Sorter.Type,
    private val pluralStr: Int,
    val ownsView: Boolean,
    defaultLayoutType: LayoutType,
    private val isSubFragment: Boolean = false,
    rawOrderExposed: Boolean = false,
    private val allowDiffUtils: Boolean = false,
    private val canSort: Boolean = true,
    private val fallbackSpans: Int = 1
) : AdapterFragment.BaseInterface<BaseAdapter<T>.ViewHolder>(), PopupTextProvider, ItemHeightHelper {

    val context = fragment.requireContext()
    protected val liveDataAgent = MutableStateFlow(liveData)
    protected inline val mainActivity
        get() = context as MainActivity
    internal inline val layoutInflater: LayoutInflater
        get() = fragment.layoutInflater
    protected var scope: CoroutineScope? = null
    private val listHeight = context.resources.getDimensionPixelSize(R.dimen.list_height)
    private val largerListHeight =
        context.resources.getDimensionPixelSize(R.dimen.larger_list_height)
    private var gridHeight: Int? = null
    private var lockedInGridSize = false
    private val sorter = Sorter(sortHelper, naturalOrderHelper, rawOrderExposed)
    val decorAdapter by lazy { createDecorAdapter() }
    override val concatAdapter by lazy { ConcatAdapter(decorAdapter, this) }
    override val itemHeightHelper by lazy {
        DefaultItemHeightHelper.concatItemHeightHelper(decorAdapter, { 1 }, this)
    }
    protected var list: Pair<List<T>, List<T>>
    private var layoutManager: RecyclerView.LayoutManager? = null
    protected var recyclerView: MyRecyclerView? = null
        private set

    private var prefs = PreferenceManager.getDefaultSharedPreferences(context)

    @Suppress("LeakingThis")
    private var prefSortType: Sorter.Type = Sorter.Type.valueOf(
        prefs.getStringStrict(
            "S" + getAdapterType(this).toString(),
            Sorter.Type.None.toString()
        )!!
    )

    private var gridPaddingDecoration = GridPaddingDecoration(context)

    @Suppress("LeakingThis")
    private var prefLayoutType: LayoutType = LayoutType.valueOf(
        prefs.getStringStrict(
            "L" + getAdapterType(this).toString(),
            LayoutType.NONE.toString()
        )!!
    )

    var layoutType: LayoutType? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == LayoutType.GRID && value != LayoutType.GRID) {
                recyclerView?.removeItemDecoration(gridPaddingDecoration)
            }
            field = value
            if (value != null && ownsView) {
                layoutManager = if (value != LayoutType.GRID
                    && context.resources.configuration.orientation
                    == Configuration.ORIENTATION_PORTRAIT
                )
                    LinearLayoutManager(context)
                else CustomGridLayoutManager(
                    context, if (value != LayoutType.GRID
                        || context.resources.configuration.orientation
                        == Configuration.ORIENTATION_PORTRAIT
                    ) 2 else 4
                )
                if (recyclerView != null) {
                    applyLayoutManager()
                }
            }
            if (recyclerView != null && recyclerView!!.width != 0)
                calculateGridSizeIfNeeded()
            lockedInGridSize = false
            notifyDataSetChanged() // we change view type for all items
        }
    val sortType: MutableStateFlow<Sorter.Type> = MutableStateFlow(
        if (prefSortType != Sorter.Type.None && prefSortType != initialSortType
            && sortTypes.contains(prefSortType) && !isSubFragment
        )
            prefSortType
        else
            initialSortType
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    private val flow = liveDataAgent.flatMapLatest { it }.combine(sortType) { it, st ->
        it to ArrayList(it).apply {
            val cmp = sorter.getComparator(st)
            if (st == Sorter.Type.NativeOrderDescending) {
                reverse()
            } else if (st != Sorter.Type.NativeOrder) {
                sortWith { o1, o2 ->
                    if (isPinned(o1) && !isPinned(o2)) -1
                    else if (!isPinned(o1) && isPinned(o2)) 1
                    else cmp?.compare(o1, o2) ?: 0
                }
            }
        }.toList()
    }.shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.WhileSubscribed(5000), replay = 1)
    val sortTypes: Set<Sorter.Type>
        get() = if (canSort) sorter.getSupportedTypes() else setOf(Sorter.Type.None)

    init {
        list = runBlocking { flow.first() }
        onListUpdated()
        layoutType =
            if (prefLayoutType != LayoutType.NONE && prefLayoutType != defaultLayoutType && !isSubFragment)
                prefLayoutType
            else
                defaultLayoutType
    }

    protected open val defaultCover: Int = R.drawable.ic_default_cover

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val songCover: ImageView = view.findViewById(R.id.cover)
        val nowPlaying: ImageView = view.findViewById(R.id.now_playing)
        val title: TextView = view.findViewById(R.id.title)
        val subTitle: TextView = view.findViewById(R.id.artist)
        val moreButton: MaterialButton = view.findViewById(R.id.more)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        if (ownsView) {
            recyclerView.setHasFixedSize(true)
            if (recyclerView.layoutManager != layoutManager) {
                applyLayoutManager()
            }
        }
        if (scope != null)
            throw IllegalStateException("scope != null in onAttachedToRecyclerView")
        scope = CoroutineScope(Dispatchers.Default)
        scope!!.launch {
            flow.collect {
                // The replay cache may cause us seeing the same list more than one. Make sure to
                // use === (reference equals) to avoid performance hit.
                if (list === it) return@collect
                val diff = if ((list.second.isNotEmpty<T>() && it.second.isNotEmpty<T>())
                    || allowDiffUtils)
                            DiffUtil.calculateDiff(SongDiffCallback(list.second, it.second))
                else null
                val sizeChanged = list.second.size != it.second.size
                withContext(Dispatchers.Main + NonCancellable) {
                    list = it
                    if (diff != null)
                        diff.dispatchUpdatesTo(this@BaseAdapter)
                    else
                        notifyDataSetChanged()
                    if (sizeChanged) decorAdapter.updateSongCounter()
                    onListUpdated()
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (layoutType == LayoutType.GRID) {
            recyclerView.removeItemDecoration(gridPaddingDecoration)
        }
        scope!!.cancel()
        scope = null
        this.recyclerView = null
        if (ownsView) {
            recyclerView.layoutManager = null
        }
    }

    private fun applyLayoutManager() {
        // If a layout manager has already been set, get current scroll position.
        val scrollPosition = if (recyclerView?.layoutManager != null) {
            (recyclerView!!.layoutManager as LinearLayoutManager)
                .findFirstCompletelyVisibleItemPosition()
        } else 0
        recyclerView?.layoutManager = layoutManager
        if (layoutType == LayoutType.GRID) {
            recyclerView?.addItemDecoration(gridPaddingDecoration)
        }
        recyclerView?.scrollToPosition(scrollPosition)
    }

    override fun getItemCount(): Int = list.second.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            layoutInflater.inflate(viewType, parent, false),
        )

    fun sort(selector: Sorter.Type) {
        sortType.value = selector
    }

    protected open fun onListUpdated() {}

    protected open fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<T>> {
        return BaseDecorAdapter(this, pluralStr, isSubFragment)
    }

    override fun getItemViewType(position: Int): Int {
        return when (layoutType) {
            LayoutType.GRID -> R.layout.adapter_grid_card
            LayoutType.COMPACT_LIST -> R.layout.adapter_list_card
            LayoutType.LIST, null -> R.layout.adapter_list_card_larger
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        if (layoutType == LayoutType.GRID) {
            lockedInGridSize = true
            val newHeight = gridHeight!!
            if (holder.itemView.layoutParams.height != newHeight) {
                holder.itemView.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = newHeight
                }
            }
        }
        val item = list.second[position]
        holder.title.text = titleOf(item) ?: virtualTitleOf(item)
        holder.subTitle.text = subTitleOf(item)
        holder.songCover.load(coverOf(item)) {
            placeholderScaleToFit(defaultCover)
            crossfade(true)
            error(defaultCover)
        }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.moreButton.setOnClickListener {
            val popupMenu = PopupMenu(it.context, it)
            onMenu(item, popupMenu)
            popupMenu.show()
        }
    }

    // need to call notifyDataSetChanged() afterwards, unless lockedInGridSize == false
    private fun calculateGridSizeIfNeeded() {
        if (layoutType != LayoutType.GRID) return
        if (recyclerView != null && recyclerView!!.width != 0) {
            val cardPadding =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_side_padding)
            val marginTop = context.resources.getDimensionPixelSize(R.dimen.grid_card_margin_top)
            val marginLabel =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_margin_label)
            val paddingBottom =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_padding_bottom)
            val labelHeight =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_label_height)
            // first find out cover's width...
            var w = recyclerView!!.width
            w -= recyclerView!!.paddingLeft + recyclerView!!.paddingRight // view padding
            w -= 2 * cardPadding // item decoration
            w /= (layoutManager as? GridLayoutManager)?.spanCount
                ?: fallbackSpans // we want width of one item
            w -= 2 * cardPadding // side padding
            // ...then use it to calculate height
            var h = w // cover is constrained 1:1
            h += marginTop // top padding of cover
            h += labelHeight // account for label height
            h += 2 * marginLabel // label vertical margin
            h += paddingBottom // bottom padding of whole card
            gridHeight = h
        } else {
            throw IllegalStateException("$recyclerView == null || ${recyclerView?.width} == 0")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onWidthChanged(width: Int) {
        calculateGridSizeIfNeeded()
        if (lockedInGridSize) {
            lockedInGridSize = false
            Log.w(
                "BaseAdapter",
                "RecyclerView width changed after locking, this must not happen during startup"
            )
            notifyDataSetChanged()
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = null
        holder.nowPlaying.setImageDrawable(null)
        holder.nowPlaying.visibility = View.GONE
        holder.songCover.dispose()
        super.onViewRecycled(holder)
    }

    private fun toId(item: T): String {
        return sorter.sortingHelper.getId(item)
    }

    private fun titleOf(item: T): String? {
        return if (sorter.sortingHelper.canGetTitle())
            sorter.sortingHelper.getTitle(item) else "null"
    }

    protected abstract fun virtualTitleOf(item: T): String
    private fun subTitleOf(item: T): String {
        return if (sorter.sortingHelper.canGetArtist())
            sorter.sortingHelper.getArtist(item) ?: context.getString(R.string.unknown_artist)
        else if (sorter.sortingHelper.canGetSize()) {
            val s = sorter.sortingHelper.getSize(item)
            return context.resources.getQuantityString(
                R.plurals.songs, s, s
            )
        } else "null"
    }

    private fun coverOf(item: T): Uri? {
        return sorter.sortingHelper.getCover(item)
    }

    protected abstract fun onClick(item: T)
    protected abstract fun onMenu(item: T, popupMenu: PopupMenu)
    private fun isPinned(item: T): Boolean {
        return titleOf(item) == null
    }

    private inner class SongDiffCallback(
        private val oldList: List<T>,
        private val newList: List<T>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = toId(oldList[oldItemPosition]) == toId(newList[newItemPosition])

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition] == newList[newItemPosition]
    }

    protected fun toRawPos(item: T): Int {
        return list.first.indexOf(item)
    }

    final override fun getPopupText(view: View, position: Int): CharSequence {
        // position here refers to pos in ConcatAdapter(!)
        // 1 == decorAdapter.itemCount
        // if this crashes with IndexOutOfBoundsException, list access isn't guarded enough?
        // lib only ever gets popup text for what RecyclerView believes to be the first view
        return (if (position >= 1)
            sorter.getFastScrollHintFor(list.second[position - 1], sortType.value)
        else null) ?: "-"
    }

    enum class LayoutType {
        NONE, LIST, COMPACT_LIST, GRID
    }

    open class StoreItemHelper<T : Item>(
        typesSupported: Set<Sorter.Type> = setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending
        )
    ) : Sorter.Helper<T>(typesSupported) {
        override fun getId(item: T): String {
            return item.id.toString()
        }

        override fun getTitle(item: T): String? {
            return item.title
        }

        override fun getSize(item: T): Int {
            return item.songList.size
        }

        override fun getCover(item: T): Uri? {
            return item.songList.firstOrNull()?.mediaMetadata?.artworkUri
        }
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        val count = ((to / ((layoutManager as? GridLayoutManager)?.spanCount ?: fallbackSpans)
            .toFloat()) + 0.5f).toInt()
        return count * when (layoutType) {
            LayoutType.GRID -> gridHeight!!
            LayoutType.COMPACT_LIST -> listHeight
            LayoutType.LIST, null -> largerListHeight
            else -> throw IllegalArgumentException()
        }
    }
}