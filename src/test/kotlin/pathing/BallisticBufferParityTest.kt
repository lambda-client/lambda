package pathing

import com.lambda.pathing.launch.ArcSample
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Bit fingerprints recorded before changing storage, including failed and truncated arcs. */
class BallisticBufferParityTest {
    private val profiles = listOf(
        BallisticProfile.VANILLA,
        BallisticProfile.VANILLA.copy(gravity = 0.04, jumpVelocity = 0.6),
        BallisticProfile.VANILLA.copy(slipperiness = 0.98),
    )

    @Test
    fun `fly preserves every sampled double and endpoint`() {
        val fingerprint = Fingerprint()
        for (profile in profiles) for (mode in LaunchMode.entries) {
            for (speed in listOf(0.0, 0.1, 0.25, 0.6)) for (rise in listOf(-8.0, -3.0, -1.0, 0.0, 0.5, 1.0, 2.0)) {
                for (held in listOf(false, true)) for (hold in listOf(0, 1, 3, Int.MAX_VALUE)) {
                    for (limit in listOf(0, 1, 5, 24, 64)) {
                        fingerprint.add(profile.fly(mode, speed, rise, held, limit, hold))
                    }
                }
            }
        }
        assertEquals("b4e28f4a2106658090da67c84e4b9fc67cb2192d463de41d8e85016cdb85a151", fingerprint.hex())
    }

    @Test
    fun `bounce preserves every sampled double and endpoint`() {
        val random = Random(843219)
        val fingerprint = Fingerprint()
        repeat(4096) {
            fingerprint.add(profiles.random(random).bounce(
                entrySpeed = random.nextDouble(0.0, 0.8), drop = listOf(0.5, 1.0, 4.0, 8.0).random(random),
                rise = listOf(-5.0, -2.0, 0.0, 2.0).random(random), holdForward = random.nextBoolean(),
                sprint = random.nextBoolean(), jump = random.nextBoolean(),
                holdTicks = listOf(0, 1, 3, Int.MAX_VALUE).random(random),
                bounceFactor = listOf(0.0, 0.6600000262260437, 1.0).random(random),
                maxTicks = listOf(0, 1, 12, 64).random(random),
                headroom = listOf(0.4, 1.0, Double.POSITIVE_INFINITY).random(random),
            ))
        }
        assertEquals("edf43bd1d48d335fca8b3359f54397913dda9a782c3c9c18ef2ce81ab781a317", fingerprint.hex())
    }

    @Test
    fun `negative capacities are rejected and returned arrays are independent`() {
        val profile = BallisticProfile.VANILLA
        assertFailsWith<IllegalArgumentException> { profile.fly(LaunchMode.SPRINT_JUMP, 0.1, 0.0, maxTicks = -1) }
        assertFailsWith<IllegalArgumentException> { profile.bounce(0.1, 4.0, -2.0, maxTicks = -1) }
        val first = profile.fly(LaunchMode.SPRINT_JUMP, 0.1, 0.0)!!
        val second = profile.fly(LaunchMode.SPRINT_JUMP, 0.1, 0.0)!!
        val value = second.heights[0]
        val distance = second.distances[0]
        first.heights[0] = 100.0
        first.distances[0] = 100.0
        assertEquals(value, second.heights[0])
        assertEquals(distance, second.distances[0])
        assertEquals(second.airTicks + 1, second.heights.size)
        assertEquals(second.airTicks + 1, second.distances.size)
        assertEquals(second.distance.toBits(), second.distances.last().toBits())
    }

    private class Fingerprint {
        private val digest = MessageDigest.getInstance("SHA-256")
        private fun add(value: Long) {
            for (shift in 0..56 step 8) digest.update((value ushr shift).toByte())
        }
        fun add(arc: ArcSample?) {
            if (arc == null) { add(-1); return }
            add(arc.airTicks.toLong())
            add(arc.distance.toBits())
            add(arc.exitSpeed.toBits())
            add(arc.heights.size.toLong())
            arc.heights.forEach { add(it.toBits()) }
            add(arc.distances.size.toLong())
            arc.distances.forEach { add(it.toBits()) }
        }
        fun hex(): String = digest.digest().joinToString("") { "%02x".format(it) }
    }
}
