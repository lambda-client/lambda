package com.lambda.pathing.trajectory

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.TrajectoryDecision
import net.minecraft.util.math.Vec3d

class CandidatePath(val points: List<Vec3d>, val best: Boolean)

interface SearchProbe {
    val candidatesEnabled: Boolean get() = false

    fun decision(action: TrajectoryDecision, rejected: Boolean, frame: Int) {}

    fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {}

    fun blocked(
        frame: Int,
        sectionX: Int,
        sectionY: Int,
        sectionZ: Int,
        capturable: Boolean,
        stance: Stance,
        movement: MovementId,
    ) {}

    fun sync(sections: Int, mutations: Int, chunks: Int, routeAffected: Boolean, extending: Boolean) {}

    fun candidates(lines: List<CandidatePath>) {}

    companion object {
        val NONE: SearchProbe = object : SearchProbe {}
    }
}
