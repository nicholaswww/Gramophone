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
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import uk.akane.libphonograph.items.FileNode

class DetailedFolderAdapter(
    private val fragment: Fragment,
    private val isDetailed: Boolean
) : AdapterFragment.BaseInterface<RecyclerView.ViewHolder>() {
    private val mainActivity = fragment.requireActivity() as MainActivity
    private val liveData = if (isDetailed) mainActivity.reader.folderStructureFlow
        else mainActivity.reader.shallowFolderFlow
    private var scope: CoroutineScope? = null
    private val folderPopAdapter: FolderPopAdapter = FolderPopAdapter(this)
    private val folderAdapter: FolderListAdapter =
        FolderListAdapter(listOf(), mainActivity, this)
    private val songList = MutableStateFlow(listOf<MediaItem>())
    private val songAdapter: SongAdapter =
        SongAdapter(fragment, songList, false, null, false)
    override val concatAdapter: ConcatAdapter =
        ConcatAdapter(this, folderPopAdapter, folderAdapter, songAdapter)
    override val itemHeightHelper: ItemHeightHelper? = null
    private var root: FileNode? = null
    private var fileNodePath = ArrayList<String>()
    private var recyclerView: MyRecyclerView? = null

    init {
        liveData.replayCache.lastOrNull()?.let { onChanged(it) }
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        this.scope = CoroutineScope(Dispatchers.Default)
        this.scope!!.launch {
            liveData.collect {
                withContext(Dispatchers.Main) {
                    onChanged(it)
                }
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope!!.cancel()
        scope = null
        recyclerView.layoutManager = null
    }

    fun onChanged(value: FileNode) {
        root = value
        if (fileNodePath.isEmpty() && isDetailed) {
            val stg = value.folderList.values.firstOrNull { it.folderName == "storage" }
            val emu = stg?.folderList?.values?.firstOrNull { it.folderName == "emulated" }
            val usr = emu?.folderList?.values?.firstOrNull()
            if (stg != null) {
                fileNodePath.add(stg.folderName)
            }
            if (emu != null) {
                fileNodePath.add(emu.folderName)
            }
            if (usr != null) {
                fileNodePath.add(usr.folderName)
            }
        }
        update(null)
    }

    fun enter(path: String?) {
        if (path != null) {
            fileNodePath.add(path)
            update(false)
        } else if (fileNodePath.isNotEmpty()) {
            fileNodePath.removeAt(fileNodePath.lastIndex)
            update(true)
        }
    }

    private fun update(invertedDirection: Boolean?) {
        var item = root
        for (path in fileNodePath) {
            item = item?.folderList?.get(path)
        }
        if (item == null) {
            fileNodePath.clear()
            item = root
        }
        val doUpdate = { canDiff: Boolean ->
            folderPopAdapter.enabled = fileNodePath.isNotEmpty()
            folderAdapter.updateList(item?.folderList?.values ?: listOf(), canDiff)
            songList.value = item?.songList ?: listOf()
        }
        recyclerView.let {
            if (it == null || invertedDirection == null) {
                doUpdate(it != null)
                return@let
            }
            val animation = AnimationUtils.loadAnimation(
                it.context,
                if (invertedDirection) R.anim.slide_out_right else R.anim.slide_out_left
            )
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    doUpdate(false)
                    it.startAnimation(
                        AnimationUtils.loadAnimation(
                            it.context,
                            if (invertedDirection) R.anim.slide_in_left else R.anim.slide_in_right
                        )
                    )
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            it.startAnimation(animation)
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        var newPos = position
        if (newPos < folderPopAdapter.itemCount) {
            return "-"
        }
        newPos -= folderPopAdapter.itemCount
        if (newPos < folderAdapter.itemCount) {
            return folderAdapter.getPopupText(view, newPos)
        }
        newPos -= folderAdapter.itemCount
        if (newPos < songAdapter.itemCount) {
            return songAdapter.getPopupText(view, newPos + 1)
        }
        throw IllegalStateException()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        throw UnsupportedOperationException()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        throw UnsupportedOperationException()

    override fun getItemCount() = 0


    private class FolderListAdapter(
        private var folderList: List<FileNode>,
        private val activity: MainActivity,
        frag: DetailedFolderAdapter
    ) : FolderCardAdapter(frag), PopupTextProvider {

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = folderList[position]
            holder.folderName.text = item.folderName
            holder.folderSubtitle.text =
                activity.resources.getQuantityString(
                    R.plurals.items,
                    (item.folderList.size +
                            item.songList.size),
                    (item.folderList.size +
                            item.songList.size)
                )
            holder.itemView.setOnClickListener {
                folderFragment.enter(item.folderName)
            }
        }

        override fun getPopupText(view: View, position: Int): CharSequence {
            return folderList[position].folderName.first().toString()
        }

        override fun getItemCount(): Int = folderList.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateList(newCollection: Collection<FileNode>, canDiff: Boolean) {
            val newList = newCollection.toMutableList()
            if (canDiff) {
                CoroutineScope(Dispatchers.Default).launch {
                    val diffResult = DiffUtil.calculateDiff(DiffCallback(folderList, newList))
                    withContext(Dispatchers.Main) {
                        folderList = newList
                        diffResult.dispatchUpdatesTo(this@FolderListAdapter)
                    }
                }
            } else {
                folderList = newList
                notifyDataSetChanged()
            }
        }

        private inner class DiffCallback(
            private val oldList: List<FileNode>,
            private val newList: List<FileNode>,
        ) : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int,
            ) = oldList[oldItemPosition].folderName == newList[newItemPosition].folderName

            override fun areContentsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int,
            ) = oldList[oldItemPosition] == newList[newItemPosition]

        }
    }

    private class FolderPopAdapter(frag: DetailedFolderAdapter) : FolderCardAdapter(frag) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            super.onCreateViewHolder(parent, viewType).apply {
                folderName.text = parent.context.getString(R.string.upper_folder)
            }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.setOnClickListener {
                folderFragment.enter(null)
            }
        }

        var enabled = false
            set(value) {
                if (field != value) {
                    field = value
                    if (value) {
                        notifyItemInserted(0)
                    } else {
                        notifyItemRemoved(0)
                    }
                }
            }

        override fun getItemCount(): Int = if (enabled) 1 else 0
    }

    private abstract class FolderCardAdapter(val folderFragment: DetailedFolderAdapter) :
        MyRecyclerView.Adapter<FolderCardAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                folderFragment.fragment.layoutInflater
                    .inflate(R.layout.adapter_folder_card, parent, false),
            )

        inner class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val folderName: TextView = view.findViewById(R.id.title)
            val folderSubtitle: TextView = view.findViewById(R.id.subtitle)
        }
    }
}