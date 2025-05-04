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

package org.akanework.gramophone.ui.fragments

import android.content.IntentSender
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.DateAdapter
import org.akanework.gramophone.ui.adapters.DetailedFolderAdapter
import org.akanework.gramophone.ui.adapters.GenreAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

/**
 * AdapterFragment:
 *   This fragment is the container for any list that contains
 * recyclerview in the program.
 *
 * @author nift4
 */
class AdapterFragment : BaseFragment(null) {
    private lateinit var adapter: BaseInterface<*>
    private lateinit var recyclerView: MyRecyclerView
    private var pendingRequest: Bundle? = null
    private lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (savedInstanceState?.containsKey("pendingRequest") == true) {
            pendingRequest = savedInstanceState.getBundle("pendingRequest")
        }
        val rootView = inflater.inflate(R.layout.fragment_recyclerview, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerview)
        // TODO share recycled view pool across all RecyclerViews to reduce performance hit when swiping
        recyclerView.enableEdgeToEdgePaddingListener()
        adapter = createAdapter()
        recyclerView.adapter = adapter.concatAdapter
        recyclerView.setAppBar((requireParentFragment() as ViewPagerFragment).appBarLayout)
        recyclerView.fastScroll(adapter, adapter.itemHeightHelper)
        (adapter as? RequestAdapter)?.let { it1 ->
            intentSender =
                registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it1.onRequest(it.resultCode, pendingRequest.also { pendingRequest = null }
                        ?: throw IllegalStateException("pendingRequest null, why?"))
                }
        }
        return rootView
    }

    fun startRequest(sender: IntentSender, data: Bundle) {
        pendingRequest = data
        intentSender.launch(IntentSenderRequest.Builder(sender).build())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingRequest != null) {
            outState.putBundle("pendingRequest", pendingRequest)
        }
    }

    private fun createAdapter(): BaseInterface<*> {
        return when (arguments?.getInt("ID", -1)) {
            R.id.songs -> SongAdapter(this, canSort = true, helper = null, ownsView = true)
            R.id.albums -> AlbumAdapter(this)
            R.id.artists -> ArtistAdapter(this)
            R.id.genres -> GenreAdapter(this)
            R.id.dates -> DateAdapter(this)
            R.id.folders -> DetailedFolderAdapter(this, false)
            R.id.detailed_folders -> DetailedFolderAdapter(this, true)
            R.id.playlists -> PlaylistAdapter(this)
            -1, null -> throw IllegalArgumentException("unset ID value")
            else -> throw IllegalArgumentException("invalid ID value")
        }
    }

    abstract class BaseInterface<T : RecyclerView.ViewHolder>
        : MyRecyclerView.Adapter<T>(), PopupTextProvider {
        abstract val concatAdapter: ConcatAdapter
        abstract val itemHeightHelper: ItemHeightHelper?
    }

    interface RequestAdapter {
        fun onRequest(resultCode: Int, data: Bundle)
    }
}
