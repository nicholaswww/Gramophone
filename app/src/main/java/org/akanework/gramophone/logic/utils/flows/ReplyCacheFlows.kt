package org.akanework.gramophone.logic.utils.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ReplayCacheInvalidationManager(val invalidate: () -> Unit) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<ReplayCacheInvalidationManager>
}

suspend fun requireReplayCacheInvalidationManager() =
    currentCoroutineContext()[ReplayCacheInvalidationManager]
        ?: throw IllegalStateException("Replay cache invalidation not available here")

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.provideReplayCacheInvalidationManager() = object : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        val sharedFlow = collector as? MutableSharedFlow<T>
            ?: throw IllegalStateException("withReplayCacheInvalidation needs to be used _directly_ before shareIn")
        if (sharedFlow is MutableStateFlow<T>)
            throw IllegalStateException("withReplayCacheInvalidation does not support state flows")
        withContext(ReplayCacheInvalidationManager(sharedFlow::resetReplayCache)) {
            return@withContext this@provideReplayCacheInvalidationManager.collect(collector)
        }
    }
}