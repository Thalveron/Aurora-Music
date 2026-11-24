package com.craftworks.music.desktop

import kotlin.random.Random

internal enum class RepeatMode { OFF, ALL, ONE }

/** Queue order is independent of the search results or library page currently displayed. */
internal data class PlayQueue<T>(val entries: List<T> = emptyList(), val index: Int = -1) {
    init {
        require((entries.isEmpty() && index == -1) || index in entries.indices)
    }

    val current get() = entries.getOrNull(index)

    fun next(repeat: RepeatMode, automatic: Boolean): Int? = when {
        entries.isEmpty() -> null
        automatic && repeat == RepeatMode.ONE -> index
        index < entries.lastIndex -> index + 1
        repeat == RepeatMode.ALL -> 0
        else -> null
    }

    fun previous(repeat: RepeatMode): Int? = when {
        index > 0 -> index - 1
        index == 0 && repeat == RepeatMode.ALL -> entries.lastIndex
        else -> null
    }

    fun move(from: Int, to: Int): PlayQueue<T> {
        require(from in entries.indices && to in entries.indices)
        val reordered = entries.toMutableList().apply { add(to, removeAt(from)) }
        val newIndex = when {
            index == from -> to
            index in (from + 1)..to -> index - 1
            index in to until from -> index + 1
            else -> index
        }
        return PlayQueue(reordered, newIndex)
    }

    fun shuffleUpcoming(random: Random = Random.Default) =
        copy(entries = entries.take(index + 1) + entries.drop(index + 1).shuffled(random))
}
