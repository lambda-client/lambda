package com.lambda.pathing.debug

import com.lambda.Lambda.LOG
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.CandidatePath
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.SearchStatsView
import com.lambda.pathing.search.SearchTreeView
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.rollout.TrajectoryRollout

class DebugChannelProbe(
	private val verbose: Boolean = java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures"),
) : SearchProbe {
	override val candidatesEnabled: Boolean get() = PlanningDebugChannel.isActive

	override val treeEnabled: Boolean
		get() = PlanningDebugChannel.isActive && PlanningDebugChannel.treeWanted

	override fun tree(view: SearchTreeView) {
		PlanningDebugChannel.publishTree(view)
	}

	override fun stats(view: SearchStatsView) {
		PlanningDebugChannel.publishStats(view)
	}

	override fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
		PlanningDebugChannel.publishAttempt(rollout, certified, diagnostic)
	}

	override fun candidates(lines: List<CandidatePath>) {
		PlanningDebugChannel.publishCandidates(
			lines.map { PlanningDebugChannel.CandidateLine(it.points, it.best) },
		)
	}

	override fun blocked(
		frame: Int,
		sectionX: Int,
		sectionY: Int,
		sectionZ: Int,
		capturable: Boolean,
		stance: Stance,
		movement: MovementId,
	) {
		if (verbose) LOG.info(
			"[blocked] section ({}, {}, {}) capturable={} frame={} anchor={} action={}",
			sectionX, sectionY, sectionZ, capturable, frame, stance, movement,
		)
	}

	override fun sync(sections: Int, mutations: Int, chunks: Int, routeAffected: Boolean, extending: Boolean) {
		if (verbose) LOG.info(
			"[sync] sections={} mutations={} chunks={} routeAffected={} extending={}",
			sections, mutations, chunks, routeAffected, extending,
		)
	}
}
