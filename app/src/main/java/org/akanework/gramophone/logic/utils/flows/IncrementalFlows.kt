package org.akanework.gramophone.logic.utils.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.min

sealed class IncrementalCommand<T>(val after: List<T>) {
    class Begin<T>(after: List<T>) : IncrementalCommand<T>(after)
    class Insert<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
    class Remove<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
    class Move<T>(val pos: Int, val count: Int, val outPos: Int, after: List<T>) : IncrementalCommand<T>(after)
    class Update<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalCommand<T>(after)
}

inline fun <T, R> Flow<IncrementalCommand<T>>.flatMapIncremental(
    crossinline predicate: (T) -> List<R>
): Flow<IncrementalCommand<R>> = flow {
    var last: List<List<R>>? = null
    var lastFlat: List<R>? = null
    collect { command ->
        var new: List<List<R>>
        var newFlat: List<R>? = null
        when {
            command is IncrementalCommand.Begin || last == null -> {
                new = command.after.map(predicate)
                newFlat = new.flatMap { it }
                emit(IncrementalCommand.Begin(newFlat))
            }
            command is IncrementalCommand.Insert -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    val item = predicate(command.after[i])
                    totalSize += item.size
                    new.add(i, item)
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalCommand.Insert(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalCommand.Move -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                for (i in command.outPos..<command.outPos+command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += last!![i].size
                    }
                    var totalOutStart = 0
                    for (i in 0..<command.outPos) {
                        totalOutStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalCommand.Move(totalStart, totalSize, totalOutStart, newFlat))
                }
            }
            command is IncrementalCommand.Remove -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalCommand.Remove(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalCommand.Update -> {
                new = ArrayList(last!!)
                var removed = 0
                var added = 0
                for (i in command.pos..<command.pos+command.count) {
                    removed += new[i].size
                    val item = predicate(command.after[i])
                    added += item.size
                    new[i] = item
                }
                if (removed != 0 || added != 0) {
                    var baseStart = 0
                    for (i in 0..<command.pos) {
                        baseStart += new[i].size
                    }
                    val baseSize = min(added, removed)
                    val offsetStart = baseStart + baseSize
                    var offsetCount = abs(added - removed)
                    newFlat = new.flatMap { it }
                    if (removed > added) {
                        val dummy = ArrayList(lastFlat!!)
                        for (i in offsetStart..<offsetStart+offsetCount) {
                            dummy.removeAt(i)
                        }
                        emit(IncrementalCommand.Remove(offsetStart, offsetCount, dummy))
                    } else if (removed < added) {
                        val dummy = ArrayList(lastFlat!!)
                        for (i in offsetStart..<offsetStart+offsetCount) {
                            dummy.add(i, newFlat[i])
                        }
                        emit(IncrementalCommand.Insert(offsetStart, offsetCount, dummy))
                    }
                    if (removed != 0 && added != 0) {
                        emit(IncrementalCommand.Update(baseStart, baseSize, newFlat))
                    }
                }
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
        lastFlat = newFlat ?: lastFlat
    }
}

inline fun <T> Flow<IncrementalCommand<T>>.filterIncremental(
    crossinline predicate: (T) -> Boolean
): Flow<IncrementalCommand<T>> = flatMapIncremental {
    if (predicate(it)) listOf(it) else emptyList()
}

/*
   Hand-"optimized" version of:
     inline fun <T, R> Flow<IncrementalCommand<T>>.mapIncremental(
         crossinline predicate: (T) -> R
     ): Flow<IncrementalCommand<R>> = flatMapIncremental {
         listOf(predicate(it))
     }
 */
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
            command is IncrementalCommand.Move -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.removeAt(i)
                }
                for (i in command.outPos..<command.outPos+command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                emit(IncrementalCommand.Move(command.pos, command.count, command.outPos, new))
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

// TODO some sort of "groupByIncremental" that takes a key, and produces incremental list of keys with
//  incremental list of their members? (if sublists aren't incremental we can't compose to big increments)

// TODO something that combines said groups back to one "Album" or "Artist"? or should we give up nice objects and
//  expose new flows from library surface to consumers for these subgroups? maybe we don't need to compose
//  increments if we do that?