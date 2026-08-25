package com.lambda.pathing.trajectory

import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.BodyState
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

internal class ValueAnchor(
    override val state: MovementSimulationState,
    override val stance: Stance,
    val elapsed: Int,
    val collisionEvents: Int,
    val launchMargin: Int,
    val inputSwitches: Int,
    val parent: ValueAnchor?,
    val inputs: List<MovementSimulationInput>,
    val boundary: Int,
) : BodyState {
    var actions: List<TrajectoryDecision>? = null

    var actionsHazardFrame: Int? = null

    var actionsEpoch: Int = -1

    val attempted: MutableSet<TrajectoryDecision> = HashSet()

    val familyPrefixFailures: MutableMap<Any, Int> = HashMap()

    override var hazardFrame: Int? = null

    var sweptEpoch: Int = -1

    fun prefix(): List<MovementSimulationInput> {
        val chain = ArrayList<List<MovementSimulationInput>>()
        var node: ValueAnchor? = this
        while (node != null) {
            if (node.inputs.isNotEmpty()) chain += node.inputs
            node = node.parent
        }
        chain.reverse()
        return chain.flatten()
    }

    fun boundaries(): List<Int> {
        val frames = ArrayList<Int>()
        var node: ValueAnchor? = this
        while (node?.parent != null) {
            frames += node.boundary
            node = node.parent
        }
        return frames.asReversed().dropLast(1)
    }

    fun depth(): Int {
        var depth = 0
        var node: ValueAnchor? = this
        while (node?.parent != null) {
            depth++
            node = node.parent
        }
        return depth
    }

    fun descendsFrom(other: ValueAnchor): Boolean {
        var node: ValueAnchor? = this
        while (node != null) {
            if (node === other) return true
            node = node.parent
        }
        return false
    }

    fun hasVisited(visited: Stance): Boolean {
        var node: ValueAnchor? = this
        while (node != null) {
            if (node.stance == visited) return true
            node = node.parent
        }
        return false
    }
}
