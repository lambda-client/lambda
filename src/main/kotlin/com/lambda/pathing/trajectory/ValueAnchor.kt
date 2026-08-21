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
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

internal class ValueAnchor(
    val state: MovementSimulationState,
    val stance: Stance,
    val elapsed: Int,
    val collisionEvents: Int,
    val launchMargin: Int,
    val inputSwitches: Int,
    val parent: ValueAnchor?,
    val inputs: List<MovementSimulationInput>,
    val boundary: Int,
) {
    var actions: List<TrajectoryDecision>? = null

    var cursor: Int = 0

    var hazardFrame: Int? = null

    var sweptToGoal: Boolean = false

    val speed: Double get() = state.velocity.horizontalLength()

    fun heading(): Pair<Double, Double>? =
        if (speed <= 1e-6) null else state.velocity.x to state.velocity.z

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
}
