package com.craftworks.music.desktop

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlayQueueTest {
    @Test fun reorderKeepsCurrentEntryEvenWhenTitlesRepeat() {
        val q = PlayQueue(listOf("A", "B", "A", "D"), 2)
        assertEquals(0, q.move(2, 0).index)
        assertEquals(1, q.move(0, 3).index)
        assertEquals(3, q.move(3, 1).index)
        assertEquals(q.entries.take(3), q.shuffleUpcoming(Random(1)).entries.take(3))
    }

    @Test fun repeatOneOnlyAppliesToAutomaticAdvance() {
        val q = PlayQueue(listOf("A", "B"), 0)
        assertEquals(0, q.next(RepeatMode.ONE, automatic = true))
        assertEquals(1, q.next(RepeatMode.ONE, automatic = false))
        assertNull(q.copy(index = 1).next(RepeatMode.OFF, true))
        assertEquals(0, q.copy(index = 1).next(RepeatMode.ALL, true))
        assertNull(PlayQueue<String>().previous(RepeatMode.ALL))
    }
}
