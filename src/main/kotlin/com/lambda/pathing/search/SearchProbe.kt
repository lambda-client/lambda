package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import net.minecraft.util.math.Vec3d

class CandidatePath(val points: List<Vec3d>, val best: Boolean)

enum class SearchNodeRole { SPINE, BEST, OPEN, PARKED, SPENT, INTERIOR }

class SearchTreeNode(
	val position: Vec3d,
	val elapsed: Int,
	val guide: Double,
	val role: SearchNodeRole,
)

class SearchTreeEdge(
	val from: Vec3d,
	val to: Vec3d,
	val role: SearchNodeRole,
	val via: MovementId?,

	val trace: List<Vec3d> = emptyList(),
)

class SearchTreeView(
	val nodes: List<SearchTreeNode>,
	val edges: List<SearchTreeEdge>,
	@Suppress("unused")
	val totalAnchors: Int,
	@Suppress("unused")
	val truncated: Boolean,
)

class SearchStatsView(
	val expansions: Int,
	val windowExpansions: Int,
	val windowBudget: Int,
	val guideExpansions: Int,
	val temperature: Double,
	val open: Int,
	val parked: Int,
	val blocked: Int,
	val spent: Int,
	val admitted: Int,
	val merged: Int,
	val restarts: Int,
	val rootFrame: Int,
	val publishedFrame: Int,
	val horizonEnd: Int,
	val cursorFrame: Int,
	val bestScore: Int?,
	@Suppress("unused")
	val elapsedMillis: Long,
)

interface SearchProbe {
	val candidatesEnabled: Boolean get() = false

	val treeEnabled: Boolean get() = false

	fun tree(view: SearchTreeView) {}

	fun stats(view: SearchStatsView) {}

	fun decision(action: TrajectoryDecision, rejected: Boolean, frame: Int) {}

	fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {}

	fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {}

	fun blocked(
		frame: Int,
		sectionX: Int,
		sectionY: Int,
		sectionZ: Int,
		capturable: Boolean,
		stance: Stance,
		movement: MovementId,
	) {
	}

	fun sync(sections: Int, mutations: Int, chunks: Int, routeAffected: Boolean, extending: Boolean) {}

	fun braked(tipElapsed: Int, executing: Int, open: Int, parked: Int, deepestElapsed: Int) {}

	fun restarted(moving: Boolean, seedElapsed: Int, executing: Int, expansions: Int, drops: Int, spent: Int) {}

	fun publishRefused(reason: () -> String, anchorElapsed: Int, tipElapsed: Int, executing: Int) {}

	fun candidates(lines: List<CandidatePath>) {}

	fun spine(reachedElapsed: Int, rollouts: Int, stalledAt: Stance?, diagnostic: TrajectoryDiagnostic?) {}

	companion object {
		val NONE: SearchProbe = object : SearchProbe {}
	}
}
