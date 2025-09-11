package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter
import uk.akane.libphonograph.items.Playlist

class PlaylistEditFragment : BaseFragment(false) {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View? {
		val theItem = MutableSharedFlow<Playlist?>(replay = 1)
		val songList = MutableSharedFlow<List<MediaItem>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

		val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
		val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
		val collapsingToolbarLayout =
			rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
		val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
		val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
		appBarLayout.enableEdgeToEdgePaddingListener()

		val bundle = requireArguments()
		val clazz = bundle.getString("Class")
		val id = bundle.getString("Id")?.toLong()
		lifecycleScope.launch(Dispatchers.Default) {
			val item = mainActivity.reader.playlistListFlow.map {
				it.find { it.id == id && it.javaClass.name == clazz }
			}.first()
			collapsingToolbarLayout.title = item?.title
				?: context?.getString(R.string.unknown_playlist)
			theItem.emit(item)
			songList.emit(item?.songList ?: listOf())
		}

		val songAdapter =
			SongAdapter(
				this,
				songList,
				canSort = false,
				rawOrderExposed = Sorter.Type.NaturalOrder,
				helper = null,
				isEdit = true,
				canChangeLayout = false,
				isSubFragment = R.id.edit
			)

		recyclerView.enableEdgeToEdgePaddingListener()
		recyclerView.setAppBar(appBarLayout)
		recyclerView.adapter = songAdapter.concatAdapter

		// Build FastScroller.
		recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

		topAppBar.setNavigationOnClickListener {
			requireActivity().supportFragmentManager.popBackStack()
		}

		return rootView
	}
}
