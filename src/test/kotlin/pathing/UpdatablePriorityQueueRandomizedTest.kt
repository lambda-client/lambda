/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.graph.UpdatablePriorityQueue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatablePriorityQueueRandomizedTest {
    @Test
    fun `indexed heap matches a reference map through random updates and removals`() {
        repeat(40) { seed ->
            val random = Random(seed)
            val queue = UpdatablePriorityQueue<Int, Int>()
            val reference = mutableMapOf<Int, Int>()

            repeat(2_000) {
                val value = random.nextInt(80)
                when (random.nextInt(4)) {
                    0, 1 -> {
                        val key = random.nextInt(1_000)
                        queue.insert(value, key)
                        reference[value] = key
                    }
                    2 -> {
                        assertEquals(reference.remove(value) != null, queue.remove(value), "seed=$seed")
                    }
                    else -> if (reference.isNotEmpty()) {
                        val expectedMinimum = reference.values.min()
                        val popped = queue.pop()
                        assertEquals(expectedMinimum, reference.remove(popped), "seed=$seed")
                    }
                }

                assertEquals(reference.size, queue.size(), "seed=$seed")
                if (reference.isEmpty()) {
                    assertTrue(queue.isEmpty(), "seed=$seed")
                } else {
                    assertFalse(queue.isEmpty(), "seed=$seed")
                    assertEquals(reference.values.min(), queue.topKey(Int.MAX_VALUE), "seed=$seed")
                    assertEquals(reference.getValue(queue.top()), queue.topKey(Int.MAX_VALUE), "seed=$seed")
                }
            }
        }
    }
}
