package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.WorldEventBatch
import com.lambda.pathing.world.arrivalChunkSet
import com.lambda.pathing.world.mutatedChunkSet
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/** Measured coarse costs; the 1.0-tick overhead is deliberate, see docs/decisions/transition-overhead.md. */
internal val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

internal class PlanningHorizonView(
	private val backing: CoarseVoxelView,
	private val granted: Set<PathingChunk>,
) : CoarseVoxelView {
	private fun grantedAt(x: Int, z: Int): Boolean = PathingChunk(x shr 4, z shr 4) in granted

	override val simulableStanceY: IntRange get() = backing.simulableStanceY

	override fun isKnown(x: Int, y: Int, z: Int): Boolean = grantedAt(x, z) && backing.isKnown(x, y, z)

	override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
		if (grantedAt(x, z)) backing.voxel(x, y, z) else CoarseVoxel.UNKNOWN

	override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? =
		if (grantedAt(x, z)) backing.collisionShape(x, y, z) else VoxelShapes.fullCube()

	override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass =
		if (grantedAt(x, z)) backing.collisionClass(x, y, z) else CollisionClass.FULL
}

internal class CoarsePlanningState(
	val snapshot: SnapshotSimulationEnvironment,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	val goal: Stance,
	val horizonChunks: Int = 0,
	frontierSweepBudget: Int = 40_000,

	/** See [FrontierAnchors.NOTHING_CAPTURABLE]: capturable unknowns never anchor. */
	private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
) {
	private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

	private val grantedChunks = HashSet<PathingChunk>()

	private val view: CoarseVoxelView =
		if (horizonChunks <= 0) snapshot
		else PlanningHorizonView(snapshot, grantedChunks)

	/** Sections whose capture lag suppressed an anchor; demanded before every wait round. */
	private val captureLagSections = HashSet<Long>()

	private val collectCaptureLag: (Int, Int, Int) -> Unit = { x, y, z ->
		captureLagSections += net.minecraft.util.math.ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
	}

	init {
		grantChunksAround(start)
	}

	/**
	 * Capturability is scoped to the horizon: a cell past the ring is unknown by design,
	 * not by lag, so it anchors like unstreamed terrain. Without this the sweep waits
	 * forever on chunks the world can capture but the view will never show.
	 */
	private val capturableInView: (Int, Int) -> Boolean =
		if (horizonChunks <= 0) capturable
		else { cx, cz -> PathingChunk(cx, cz) in grantedChunks && capturable(cx, cz) }

	val planner = CoarsePlanner(
		view, moves, start, goal,
		sweepBudget = frontierSweepBudget, capturable = capturableInView,
		onCaptureLag = collectCaptureLag,
	)

	fun repairFrom(start: Stance, changed: Set<VoxelPos>, changedChunks: Set<PathingChunk>) {
		planner.updateStart(start)

		val revealed = grantChunksAround(start)
		if (changed.isNotEmpty()) planner.worldChanged(changed)
		if (changedChunks.isNotEmpty()) planner.chunksChanged(changedChunks)
		if (revealed.isNotEmpty()) planner.chunksChanged(revealed, arrivalsOnly = true)
	}

	/**
	 * A world batch mid-walk: mutated chunks resynchronise fully, pure arrivals only where
	 * an origin read the unknown. [maxStances] bounds the work done now, see [continueSync].
	 */
	fun applyEvents(batch: WorldEventBatch, maxStances: Int = Int.MAX_VALUE) {
		val mutated = batch.mutatedChunkSet()
		if (mutated.isNotEmpty()) planner.chunksChanged(mutated, maxStances)
		val arrivals = batch.arrivalChunkSet()
		if (arrivals.isNotEmpty()) planner.chunksChanged(arrivals, maxStances, arrivalsOnly = true)
	}

	/** Newly granted horizon chunks: the view went from unknown to known, nothing known changed. */
	fun revealChunks(revealed: Set<PathingChunk>) {
		if (revealed.isNotEmpty()) planner.chunksChanged(revealed, arrivalsOnly = true)
	}

	/** Resynchronises up to [maxStances] more graph nodes; true when work remains after this call. */
	fun continueSync(maxStances: Int): Boolean {
		if (planner.pendingSyncSize == 0) return false
		planner.continueSync(maxStances)
		return planner.pendingSyncSize > 0
	}

	/** Demand exactly the sections whose lag suppressed anchors, then forget them. */
	fun demandCaptureLag(world: PathingWorld) {
		if (captureLagSections.isEmpty()) return
		world.interest(ArrayList(captureLagSections), InterestTier.DEMAND)
		captureLagSections.clear()
	}

	/**
	 * The best route the current knowledge admits, without waiting for more: a plain
	 * extraction, then resynchronisation, then one frontier discovery. Blocking on the
	 * world is [com.lambda.pathing.session.RouteResolution]'s job.
	 */
	fun routePlan(
		snapshotRevision: Long,
		cancelled: () -> Boolean = { false },
		maxExpansions: Int = Int.MAX_VALUE,
	): CoarseRoutePlan? =
		planner.routePlan(snapshotRevision, cancelled = cancelled)
			?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled, maxExpansions = maxExpansions)
			?: run {
				if (planner.discoverReachableFrontier(cancelled, maxExpansions)) {
					planner.routePlan(snapshotRevision, cancelled = cancelled)
				} else null
			}

	/** Widens the planning horizon around [start]; returns the chunks newly granted. */
	fun grantChunksAround(start: Stance): Set<PathingChunk> {
		if (horizonChunks <= 0) return emptySet()
		val revealed = HashSet<PathingChunk>()
		val centerX = start.x shr 4
		val centerZ = start.z shr 4
		for (dx in -horizonChunks..horizonChunks) {
			for (dz in -horizonChunks..horizonChunks) {
				val chunk = PathingChunk(centerX + dx, centerZ + dz)
				if (grantedChunks.add(chunk)) revealed += chunk
			}
		}
		return revealed
	}
}
