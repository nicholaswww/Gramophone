package org.akanework.gramophone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.logic.utils.flows.IncrementalList
import org.akanework.gramophone.logic.utils.flows.PauseManager
import org.akanework.gramophone.logic.utils.flows.conflateAndBlockWhenPaused
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseableFlowsTest {
    @Test
    fun testPauseableFlows() {
        val out = arrayListOf<Int>()
        val inFlow = flow {
            var i = 0
            while (i < 10) {
                println("flow: wait 100ms")
                delay(100)
                val start = System.currentTimeMillis()
                println("flow: emit $i")
                emit(i++)
                println("flow: emit ${i-1} took ${System.currentTimeMillis() - start}")
            }
        }.conflateAndBlockWhenPaused()
        runBlocking {
            val tpm = TestPauseManager(true)
            val job = launch(Dispatchers.IO.limitedParallelism(1) + tpm) {
                inFlow.collect {
                    println("channel recv $it")
                    out.add(it)
                }
            }
            println("#start: $out")
            delay(250)
            println("#0, setting pause = false...: $out")
            tpm.isPaused.value = false
            println("#1, set pause = false!!: $out")
            delay(200)
            println("#2, setting pause = true...: $out")
            tpm.isPaused.value = true
            println("#3, set pause = true-..: $out")
            delay(300)
            println("#4, setting pause = false...: $out")
            tpm.isPaused.value = false
            println("#5, set pause = false: $out")
            delay(500)
            assertEquals(arrayListOf(1, 2, 3, 6, 7, 8, 9), out)
            job.join()
        }
    }

    class TestPauseManager(initialValue: Boolean) : PauseManager {
        override val isPaused = MutableStateFlow<Boolean>(initialValue)
    }

    @Test
    fun incrementalFlows() {
        val source = flow {
            emit(IncrementalList.Begin(listOf(1, 2, 3)))
            emit(IncrementalList.Insert(1, 2, listOf(1, 999, 2, 3)))
        }
    }
}