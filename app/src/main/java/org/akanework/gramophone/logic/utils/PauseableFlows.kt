package org.akanework.gramophone.logic.utils

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

interface PauseManager : CoroutineContext.Element {
    val isPaused: Flow<Boolean>

    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<PauseManager>
}

class LifecyclePauseManager(scope: CoroutineScope, source: LifecycleOwner, minimumState: Lifecycle.State)
    : PauseManager {
    override val isPaused = source.lifecycle.currentStateFlow.map { !it.isAtLeast(minimumState) }
        .stateIn(scope, WhileSubscribed(), !source.lifecycle.currentState.isAtLeast(minimumState))
}

object EmptyPauseManager : PauseManager {
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val isPaused = object : StateFlow<Boolean> {
        override val value: Boolean = false
        override val replayCache = listOf(false)

        override suspend fun collect(collector: FlowCollector<Boolean>): Nothing {
            collector.emit(false)
            while (true)
                delay(Duration.INFINITE)
        }

    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CountingPauseManager : PauseManager {
    private val flows = MutableStateFlow(listOf<Flow<Boolean>>())
    override val isPaused = flows.mapLatest { it.find { !it.first() } == null } // will pause when 0 items

    fun add(other: PauseManager) {
        flows.value += other.isPaused
    }

    fun remove(other: PauseManager) {
        flows.value -= other.isPaused
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PauseManagingSharedFlow<T>() : SharedFlow<T> {
    private val pauseManager = CountingPauseManager()
    lateinit var sharedFlow: SharedFlow<T>

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val pm = currentCoroutineContext()[PauseManager] ?: EmptyPauseManager
        try {
            Log.w("Tag", java.lang.IllegalStateException("register"))
            pauseManager.add(pm)
            withContext(pauseManager) {
                sharedFlow.collect(collector)
            }
        } finally {
            Log.w("Tag", java.lang.IllegalStateException("remove"))
            pauseManager.remove(pm)
        }
    }

    companion object {
        fun <T> Flow<T>.sharePauseableIn(
            scope: CoroutineScope,
            started: SharingStarted,
            replay: Int = 0
        ): SharedFlow<T> {
            val wrapper = PauseManagingSharedFlow<T>()
            wrapper.sharedFlow = shareIn(CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job])
                    + wrapper.pauseManager), started, replay)
            return wrapper
        }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PauseManagingStateFlow<T>() : StateFlow<T> {
    private val pauseManager = CountingPauseManager()
    lateinit var stateFlow: StateFlow<T>

    override val replayCache: List<T>
        get() = stateFlow.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val pm = currentCoroutineContext()[PauseManager] ?: EmptyPauseManager
        try {
            pauseManager.add(pm)
            withContext(pauseManager) {
                stateFlow.collect(collector)
            }
        } finally {
            pauseManager.remove(pm)
        }
    }

    override val value: T
        get() = stateFlow.value

    companion object {
        fun <T> Flow<T>.statePauseableIn(
            scope: CoroutineScope,
            started: SharingStarted,
            initialValue: T
        ): SharedFlow<T> {
            val wrapper = PauseManagingStateFlow<T>()
            wrapper.stateFlow = stateIn(CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job])
                    + wrapper.pauseManager), started, initialValue)
            return wrapper
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> repeatFlowWhenUnpaused(enforcePauseable: Boolean = false, block: suspend () -> T): Flow<T> {
    val pauseManager = currentCoroutineContext()[PauseManager]
    return if (pauseManager != null) {
        pauseManager.isPaused.flatMapLatest {
            Log.e("hi", "hii ${pauseManager.isPaused.first()}")
            if (!it) {
                try {
                    flowOf(block())
                } finally {
                    Log.e("hi", "byee ${pauseManager.isPaused.first()}")
                }
            }
            else emptyFlow()
        }
    } else {
        if (enforcePauseable)
            throw IllegalStateException("enforcePauseable is set to true, expected to find PauseManager")
        flowOf(block())
    }
}

suspend fun repeatWhenUnpaused(enforcePauseable: Boolean = false, block: suspend () -> Unit) {
    repeatFlowWhenUnpaused(enforcePauseable, block).collect()
}

suspend fun <T> repeatUntilDoneWhenUnpaused(enforcePauseable: Boolean = false, block: suspend () -> T): T {
    return repeatFlowWhenUnpaused(enforcePauseable, block).first()
}

fun repeatPausingWithLifecycle(source: LifecycleOwner,
                               context: CoroutineContext = EmptyCoroutineContext,
                               minimumStateForCollect: Lifecycle.State = Lifecycle.State.CREATED,
                               minimumStateForUnpause: Lifecycle.State = Lifecycle.State.RESUMED,
                               block: suspend () -> Unit) {
    source.lifecycleScope.launch {
        source.repeatOnLifecycle(minimumStateForCollect) {
            withContext(context + LifecyclePauseManager(this, source, minimumStateForUnpause)) {
                block()
            }
        }
    }
}

// Downstream will still finish processing values, to avoid, wrap downstream in repeatUntilDoneWhenUnpaused
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.bufferAndBlockWhenPaused(capacity: Int = Channel.UNLIMITED, enforcePauseable: Boolean = false): Flow<T> = channelFlow {
    coroutineScope {
        produce(capacity = capacity) {
            collect { item -> send(item) }
        }.consume {
            repeatWhenUnpaused(enforcePauseable) {
                while (true) {
                    val item = receiveCatching()
                    if (item.isClosed) {
                        close()
                        break
                    }
                    send(item.getOrThrow())
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Flow<T>.conflateAndBlockWhenPaused(enforcePauseable: Boolean = false) =
    bufferAndBlockWhenPaused(Channel.CONFLATED, enforcePauseable)