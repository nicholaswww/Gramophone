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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

/**
 * ArtistSubFragment:
 *   Separated from GeneralSubFragment and will be
 * merged into it in future development.
 *
 * @author nift4
 * @see BaseFragment
 * @see GeneralSubFragment
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ArtistSubFragment : BaseFragment(true), PopupTextProvider {
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var songAdapter: SongAdapter
    private var recyclerView: MyRecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        val id = requireArguments().getString("Id")?.toLong()
        val itemType = requireArguments().getInt("Item")
        recyclerView = rootView.findViewById(R.id.recyclerview)

        val item = mainActivity.reader.let {
            if (itemType == R.id.album_artist)
                it.albumArtistListFlow else it.artistListFlow
        }.map { it.find { it.id == id } }
        albumAdapter = AlbumAdapter(
            this, item.map { it?.albumList },
            isSubFragment = itemType
        )
        albumAdapter.decorAdapter.jumpDownPos = { albumAdapter.concatAdapter.itemCount }
        songAdapter = SongAdapter(
            this,
            item.map { it?.songList }, canSort = true, helper = null,
            isSubFragment = itemType
        )
        songAdapter.decorAdapter.jumpUpPos = { 0 }
        val ih = DefaultItemHeightHelper.concatItemHeightHelper(albumAdapter.itemHeightHelper,
            { albumAdapter.concatAdapter.itemCount }, songAdapter.itemHeightHelper
        )
        recyclerView!!.enableEdgeToEdgePaddingListener()
        recyclerView!!.adapter =
            ConcatAdapter(ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
                albumAdapter.concatAdapter, songAdapter.concatAdapter)
        recyclerView!!.setAppBar(appBarLayout)
        recyclerView!!.fastScroll(this, ih)

        topAppBar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        val title = item.map { it?.title ?: requireContext().getString(R.string.unknown_artist) }
        lifecycleScope.launch(Dispatchers.Default) {
            title.collect {
                withContext(Dispatchers.Main) {
                    // Show title text.
                    topAppBar.title = it
                }
            }
        }

        return rootView
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        return if (position < albumAdapter.concatAdapter.itemCount) {
            albumAdapter.getPopupText(view, position)
        } else {
            songAdapter.getPopupText(view, position - albumAdapter.concatAdapter.itemCount)
        }
    }
}
