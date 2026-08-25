/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.Stance
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

    /** Stable semantic identity; regenerated action ordering can never retry or skip a decision. */
    val attempted: MutableSet<TrajectoryDecision> = HashSet()

    /**
     * Earliest failure frame per family that fell *before* the failing member diverged.
     *
     * A family is every variant of one control differing only in when its launch fires,
     * so their inputs are identical until that tick. A failure at frame f therefore
     * condemns -- exactly, not heuristically -- every sibling whose own launch comes
     * after f: it would replay the same frames into the same failure.
     */
    val familyPrefixFailures: MutableMap<Any, Int> = HashMap()

    override var hazardFrame: Int? = null

    /**
     * The knowledge epoch this anchor's finish sweep last ran under. A plain latch
     * stalled a persistent search forever: a sweep that failed on then-unknown terrain
     * never retried after the terrain arrived.
     */
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
