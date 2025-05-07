package org.akanework.gramophone.logic.utils.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class IncrementalCommand<T>(val after: List<T>) {
    class Begin<T>(after: List<T>) : IncrementalCommand<T>(after)
    class Insert<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
    class Remove<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
    //class Move<T>(val pos: Int, val count: Int, val outPos: Int, after: List<T>) : IncrementalCommand<T>(after)
    class Update<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
}

inline fun <T, R> Flow<IncrementalCommand<T>>.mapIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalCommand<R>> = flow {
    var last: List<R>? = null
    collect { command ->
        var new: List<R>
        when {
            command is IncrementalCommand.Begin || last == null -> {
                new = command.after.map(predicate)
                emit(IncrementalCommand.Begin(new))
            }
            command is IncrementalCommand.Insert -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.add(i, predicate(command.after[i]))
                }
                emit(IncrementalCommand.Insert(command.pos, command.count, new))
            }
            command is IncrementalCommand.Remove -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.removeAt(i)
                }
                emit(IncrementalCommand.Remove(command.pos, command.count, new))
            }
            command is IncrementalCommand.Update -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new[i] = predicate(command.after[i])
                }
                emit(IncrementalCommand.Update(command.pos, command.count, new))
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
    }
}
/*
inline fun <T> Flow<IncrementalCommand<T>>.filterIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalCommand<U>> = flow {
    var last: List<R>? = null
    collect { command ->
        var new: List<R>
        when {
            command is IncrementalCommand.Begin || last == null -> {
                new = command.after.map(predicate)
                val out = command.after.filterIndexed { i, _ -> new[i] }
                emit(IncrementalCommand.Begin(out))
            }
            command is IncrementalCommand.Insert -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.add(i, predicate(command.after[i]))
                }
                val out = postProcess(command.after, new)
                emit(IncrementalCommand.Insert(command.pos, command.count, out))
            }
            command is IncrementalCommand.Remove -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.removeAt(i)
                }
                val out = postProcess(command.after, new)
                emit(IncrementalCommand.Remove(command.pos, command.count, out))
            }
            command is IncrementalCommand.Update -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new[i] = predicate(command.after[i])
                }
                val out = postProcess(command.after, new)
                emit(IncrementalCommand.Update(command.pos, command.count, out))
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
    }
}*/