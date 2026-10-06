package pathing

import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.coarse.FrontierSweep
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.shape.VoxelShape

internal fun streamedSnapshot(
    bounds: SimulationSnapshotBounds,
    sections: Map<Long, ImmutableSnapshotSection>,
): SnapshotSimulationEnvironment =
    SnapshotSimulationEnvironment.streaming(bounds) { error("exact simulation must never consume unstreamed terrain") }
        .apply { sections.forEach { (key, section) -> install(key, section) } }

internal fun SnapshotBlockPhysics.Companion.of(
    shape: VoxelShape,
    slipperiness: Double = DEFAULT_SLIPPERINESS,
    velocityMultiplier: Double = 1.0,
    jumpVelocityMultiplier: Double = 1.0,
    fenceLike: Boolean = false,
    bounceFactor: Double = 0.0,
    dampensSteppingSpeed: Boolean = false,
) = SnapshotBlockPhysics(
    collisionShape = shape,
    slipperiness = slipperiness,
    velocityMultiplier = velocityMultiplier,
    jumpVelocityMultiplier = jumpVelocityMultiplier,
    coarseVoxel = BlockPhysicsCapture.coarseVoxelOf(shape, bouncy = bounceFactor >= 1.0),
    fenceLike = fenceLike,
    bounceFactor = bounceFactor,
    dampensSteppingSpeed = dampensSteppingSpeed,
)

internal fun FrontierAnchors.sweep(
    view: CoarseVoxelView,
    moves: SimpleMoveLibrary,
    from: Stance,
    goal: Stance,
    capturable: (Int, Int) -> Boolean = NOTHING_CAPTURABLE,
    onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
): Map<Stance, Double> =
    FrontierSweep(view, moves, goal, { moves.edgesFrom(view, it) }, capturable = capturable, onCaptureLag = onCaptureLag)
        .anchorsFrom(from)
