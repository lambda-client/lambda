/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.InterestPrimer
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A later leg's route is planned while the walk is still on an earlier one. When the terrain then shows that
 * leg has no route after all, its last frame must not be handed to the walk: on the cylonemc course the handoff
 * took such a frame and walked twenty blocks to an unexplored corner the planner already knew led nowhere.
 */
class SupersededLegFrameTest {
	@Test
	fun `a leg whose goal gets walled in is no longer handed out from its earlier frame`() {
		val start = Stance(0, 64, 0)
		val waypoint = Stance(8, 64, 0)
		val goal = Stance(16, 64, 0)
		val source = FakeCaptureSource(groundY = 64).apply { loadSquare(4) }
		val world = PathingWorld(SimulationSnapshotBounds(Int.MIN_VALUE, -64, Int.MIN_VALUE, Int.MAX_VALUE, 319, Int.MAX_VALUE), source)
		val changes = ConcurrentLinkedQueue<BlockPos>()
		val running = AtomicBoolean(true)
		val client = thread(name = "fake-client", isDaemon = true) {
			while (running.get()) {
				while (true) world.onBlockChanged(changes.poll() ?: break)
				InterestPrimer.primeBody(world, BlockPos(start.x, start.y, start.z))
				world.advance(15.0)
				Thread.sleep(10)
			}
		}
		InterestPrimer.primeJourney(world, start, goal, listOf(waypoint))
		val options = SimpleMoveOptions()
		val legs = listOf(
			CoarsePlanningState(world.snapshot, options, start, waypoint),
			CoarsePlanningState(world.snapshot, options, waypoint, goal),
		)
		val coarse = CoarseService(world, legs, listOf(start, waypoint), expansionBudget = 200_000)
		try {
			coarse.start()
			coarse.follow(0, start, SpeedClass.STOPPED)
			assertNotNull(coarse.awaitFrame(1, 20_000), "the open floor carries the second leg")

			for (dx in -1..1) for (dz in -1..1) if (dx != 0 || dz != 0) for (y in 64..66) {
				source.set(goal.x + dx, y, goal.z + dz, SnapshotBlockPhysics.FULL_CUBE)
				changes += BlockPos(goal.x + dx, y, goal.z + dz)
			}
			val deadline = System.nanoTime() + 20_000_000_000L
			while (coarse.failure(1, waypoint) == null && System.nanoTime() < deadline) Thread.sleep(20)
			assertNotNull(coarse.failure(1, waypoint), "the walled-in goal leaves the second leg without a route")
			assertNull(coarse.awaitFrame(1, 200), "the frame planned before the wall must not be handed out")
		} finally {
			coarse.close()
			running.set(false)
			client.join()
		}
	}
}
