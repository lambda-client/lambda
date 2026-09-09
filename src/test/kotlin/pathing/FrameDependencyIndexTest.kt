package pathing

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.search.FrameDependencyIndex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FrameDependencyIndexTest {
    @Test
    fun `chunk and section queries match original first and last maps`() {
        val random = Random(946)
        repeat(100) {
            val positions = List(30) {
                VoxelPos(random.nextInt(-33, 34), random.nextInt(-33, 34), random.nextInt(-33, 34))
            }
            val frames = List(random.nextInt(0, 25)) {
                List(random.nextInt(0, 15)) { positions.random(random) }.toSet()
            }
            verify(frames, PathingChunk::containing)
            verify(frames, PathingSection::containing)
        }
    }

    @Test
    fun `read gaps remain conservatively included and results are independent`() {
        val position = VoxelPos(-1, -17, 16)
        val index = FrameDependencyIndex(listOf(setOf(position), emptySet(), setOf(position)), PathingSection::containing)
        val section = PathingSection(-1, -2, 1)
        assertEquals(setOf(section), index.between(1, 2))
        assertEquals(0, index.first(section))
        assertEquals(null, index.first(PathingSection(0, 0, 0)))
        assertEquals(emptySet(), index.from(3))
        (index.from(0) as MutableSet).clear()
        assertEquals(setOf(section), index.from(0))
    }

    private fun <K : Any> verify(frames: List<Set<VoxelPos>>, keyOf: (VoxelPos) -> K) {
        val first = linkedMapOf<K, Int>()
        val last = linkedMapOf<K, Int>()
        frames.forEachIndexed { frame, positions ->
            positions.forEach {
                first.putIfAbsent(keyOf(it), frame)
                last[keyOf(it)] = frame
            }
        }
        val index = FrameDependencyIndex(frames, keyOf)
        first.forEach { (key, frame) -> assertEquals(frame, index.first(key)) }
        for (next in 0..frames.size) {
            assertEquals(last.filterValues { it >= next }.keys.toList(), index.from(next).toList())
            for (until in -1..frames.size + 1) {
                val expected = last.filter { (key, frame) -> frame >= next && first.getValue(key) < until }.keys
                assertEquals(expected.toList(), index.between(next, until).toList())
            }
        }
    }
}
