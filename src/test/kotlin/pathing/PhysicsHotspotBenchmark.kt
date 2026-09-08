package pathing

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.time.measureTime

@Tag("bench")
class PhysicsHotspotBenchmark {
    @Test
    fun benchmark() {
        val profile = BallisticProfile.VANILLA
        measure("fly") { i -> profile.fly(LaunchMode.SPRINT_JUMP, 0.1 + (i % 8) * 0.05, -(i % 3).toDouble()) }
        measure("bounce") { i -> profile.bounce(0.1 + (i % 8) * 0.05, 4.0, -2.0, sprint = true) }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 90, -8, 8, 110, 8),
            buildMap { for (x in -5..5) for (z in -5..5) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE) },
        )
        val boxes = List(8) { i -> Box(-0.3 + i * 0.1, 99.999999, 0.2, 0.3 + i * 0.1, 100.0, 0.8) }
        val positions = List(8) { i -> Vec3d(i * 0.1, 100.0, 0.5) }
        measure("support") { i -> environment.findSupportingBlockPos(boxes[i % 8], positions[i % 8]) }
    }

    private fun measure(name: String, operation: (Int) -> Any?) {
        val warmUntil = System.nanoTime() + 1_000_000_000L
        do { repeat(1000) { sink = operation(it) } } while (System.nanoTime() < warmUntil)
        val iterations = 20_000
        val samples = List(5) {
            measureTime { repeat(iterations) { sink = operation(it) } }.inWholeNanoseconds.toDouble() / iterations / 1000
        }
        val bytes = PlannerBenchmark.allocatedBytes { repeat(iterations) { sink = operation(it) } } / iterations
        println("[physics-hotspot] $name median-us=${samples.sorted()[2]} bytes/op=$bytes samples=$samples")
    }

    companion object {
        @Volatile private var sink: Any? = null
    }
}
