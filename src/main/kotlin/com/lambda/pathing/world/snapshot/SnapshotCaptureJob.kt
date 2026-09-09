package com.lambda.pathing.world.snapshot

import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.World

internal class SnapshotCaptureJob(
	private val world: World,
	player: ClientPlayerEntity,
	val bounds: SimulationSnapshotBounds,
) {
	private val sections = HashMap<Long, ImmutableSnapshotSection>()
	private val mutable = BlockPos.Mutable()
	private val shapeContext = ShapeContext.of(player)
	private val physicsInterner = BlockPhysicsInterner(shapeContext)

	private val minSectionX = bounds.minX shr 4
	private val maxSectionX = bounds.maxX shr 4
	private val minSectionY = bounds.minY shr 4
	private val maxSectionY = bounds.maxY shr 4
	private val minSectionZ = bounds.minZ shr 4
	private val maxSectionZ = bounds.maxZ shr 4

	private var sectionX = minSectionX
	private var sectionY = minSectionY
	private var sectionZ = minSectionZ
	private var x = sectionMinX()
	private var y = sectionMinY()
	private var z = sectionMinZ()
	private var builder = ImmutableSnapshotSection.Builder()
	private var sectionWrites = 0
	private var capturedCells = 0L
	private var completed: SnapshotSimulationEnvironment? = null

	val totalCells: Long =
		(bounds.maxX.toLong() - bounds.minX + 1L) *
				(bounds.maxY.toLong() - bounds.minY + 1L) *
				(bounds.maxZ.toLong() - bounds.minZ + 1L)

	fun advance(maxCells: Int, deadlineNanos: Long): SnapshotCaptureResult {
		check(MinecraftClient.getInstance().isOnThread) {
			"Simulation snapshots must be captured on the client thread"
		}
		require(maxCells > 0) { "Snapshot capture cell quota must be positive" }
		completed?.let { return SnapshotCaptureResult.Complete(it) }

		var capturedThisCall = 0
		while (capturedThisCall < maxCells &&
			(capturedThisCall == 0 || System.nanoTime() < deadlineNanos)
		) {
			if (!world.isChunkLoaded(sectionX, sectionZ)) {
				return SnapshotCaptureResult.Failed(
					"snapshot requires unloaded chunk ($sectionX, $sectionZ); move closer and retry",
				)
			}

			val pos = mutable.set(x, y, z)
			val physics = physicsInterner.capture(world, pos, world.getBlockState(pos))
			builder.set(x, y, z, physics)
			sectionWrites++
			capturedCells++
			capturedThisCall++

			if (advanceCell()) {
				sections[ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)] =
					builder.build(expectedWrites = sectionWrites)
				if (!advanceSection()) {
					val snapshot = SnapshotSimulationEnvironment(bounds, sections, defaultBlock = null)
					completed = snapshot
					return SnapshotCaptureResult.Complete(snapshot)
				}
				builder = ImmutableSnapshotSection.Builder()
				sectionWrites = 0
				x = sectionMinX()
				y = sectionMinY()
				z = sectionMinZ()
			}
		}
		return SnapshotCaptureResult.Progress(capturedCells, totalCells)
	}

	private fun advanceCell(): Boolean {
		if (x < sectionMaxX()) {
			x++
			return false
		}
		x = sectionMinX()
		if (z < sectionMaxZ()) {
			z++
			return false
		}
		z = sectionMinZ()
		if (y < sectionMaxY()) {
			y++
			return false
		}
		return true
	}

	private fun advanceSection(): Boolean {
		if (sectionX < maxSectionX) {
			sectionX++
			return true
		}
		sectionX = minSectionX
		if (sectionZ < maxSectionZ) {
			sectionZ++
			return true
		}
		sectionZ = minSectionZ
		if (sectionY < maxSectionY) {
			sectionY++
			return true
		}
		return false
	}

	private fun sectionMinX() = maxOf(bounds.minX, sectionX shl 4)
	private fun sectionMaxX() = minOf(bounds.maxX, (sectionX shl 4) + 15)
	private fun sectionMinY() = maxOf(bounds.minY, sectionY shl 4)
	private fun sectionMaxY() = minOf(bounds.maxY, (sectionY shl 4) + 15)
	private fun sectionMinZ() = maxOf(bounds.minZ, sectionZ shl 4)
	private fun sectionMaxZ() = minOf(bounds.maxZ, (sectionZ shl 4) + 15)
}
