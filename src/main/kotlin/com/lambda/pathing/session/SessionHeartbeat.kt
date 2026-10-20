package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.world.streaming.PathingWorld

internal class SessionHeartbeat {
	private var nextAt = 0L
	private var trackedJob: PlanningJob? = null
	private var trackedWorld: PathingWorld? = null
	private var expansions = 0
	private var publications = 0
	private var refusals = 0
	private var reasons: Map<String, Int> = emptyMap()
	private var certifications = 0
	private var failures = 0
	private var replayed = 0L
	private var derived = 0
	private var blockChanges = 0L
	private var reloads = 0L
	private var changedReloads = 0L
	private var forgotten = 0L
	private var misses = 0L
	private var adopted = 0
	private var rejected = 0

	fun soon() {
		nextAt = 0L
	}

	fun SafeContext.beat(walk: PathingSession) {
		val now = System.currentTimeMillis()
		if (now < nextAt) return
		nextAt = now + INTERVAL_MILLIS
		LOG.info("Pathing heartbeat: {}", describe(walk, now))
	}

	private fun SafeContext.describe(walk: PathingSession, now: Long): String = buildString {
		append(walk.state).append(" at ").append(player.blockPos.toShortString())
		append(" speed %.3f".format(player.velocity.horizontalLength()))
		if (player.air < player.maxAir) append(" air ").append(player.air)
		walk.replay?.let { replay ->
			append(" | tape f").append(replay.cursor.nextFrame).append('/').append(replay.path.plan.tape.frameCount)
			if (replay.holding) append(", holding ").append(seconds(now - replay.heldSince))
		}
		val telemetry = walk.telemetry.snapshot()
		append(" | adopted +").append(telemetry.adopted - adopted)
		append(", rejected +").append(telemetry.rejectedImprovements - rejected)
		append(", recoveries ").append(telemetry.recoveries).append(", pushes ").append(telemetry.pushes).append(", holds ").append(telemetry.holds)
		append(", verified world changes ").append(walk.telemetry.verifiedChanges)
		adopted = telemetry.adopted
		rejected = telemetry.rejectedImprovements
		describeJob(walk.launcher.current?.job ?: walk.launcher.lastJob, now)
		walk.journey?.let { journey ->
			describeWorld(journey.world)
			append(" | coarse ").append(journey.coarse.activity())
		}
	}

	private fun StringBuilder.describeJob(job: PlanningJob?, now: Long) {
		if (job == null) {
			append(" | no planning job")
			return
		}
		if (job !== trackedJob) {
			trackedJob = job
			expansions = 0
			publications = 0
			refusals = 0
			reasons = emptyMap()
			certifications = 0
			failures = 0
			replayed = 0L
		}
		append(" | job ").append(job.phase).append(" for ").append(seconds(now - job.phaseSince))
		append(", published +").append(job.publications - publications)
		publications = job.publications
		val stats = job.stats ?: return
		append(" | search (").append(seconds(now - job.statsAt)).append(" old) +").append(stats.expansions - expansions).append(" expansions")
		append(", root f").append(stats.rootFrame)
		stats.rootStance?.let { append(' ').append(it) }
		append(", tip f").append(stats.publishedFrame).append(", cursor f").append(stats.cursorFrame)
		append(", open ").append(stats.openAnchors).append(", parked ").append(stats.parkedAnchors).append(", blocked ").append(stats.blockedAttempts)
		append(", restarts ").append(stats.tapeRestarts).append(", route ").append(stats.deepestRouteIndex).append('/').append(stats.routeNodes)
		append(", refusals +").append(stats.publishRefusals - refusals)
		val fresh = stats.refusalReasons.mapNotNull { (reason, count) ->
			(count - (reasons[reason] ?: 0)).takeIf { it > 0 }?.let { "$reason $it" }
		}
		if (fresh.isNotEmpty()) append(fresh.joinToString(", ", " (", ")"))
		append(", certified +").append(stats.certifications - certifications)
		append(" replaying +").append(stats.replayedFrames - replayed).append(" frames")
		if (stats.certifyFailures > failures) {
			append(", certify failures +").append(stats.certifyFailures - failures).append(" (last: ").append(stats.lastCertifyFailure).append(')')
		}
		expansions = stats.expansions
		refusals = stats.publishRefusals
		reasons = stats.refusalReasons
		certifications = stats.certifications
		failures = stats.certifyFailures
		replayed = stats.replayedFrames
	}

	private fun StringBuilder.describeWorld(world: PathingWorld) {
		if (world !== trackedWorld) {
			trackedWorld = world
			derived = 0
			blockChanges = 0L
			reloads = 0L
			changedReloads = 0L
			forgotten = 0L
			misses = 0L
		}
		append(" | world +").append(world.sectionsDerived - derived).append(" sections derived")
		append(", block changes +").append(world.blockChanges - blockChanges)
		append(", reloads +").append(world.chunkReloads - reloads).append(" (changed +").append(world.reloadsChanged - changedReloads).append(')')
		append(", forgotten chunks +").append(world.chunksForgotten - forgotten)
		append(", exact misses +").append(world.exactMisses - misses)
		derived = world.sectionsDerived
		blockChanges = world.blockChanges
		reloads = world.chunkReloads
		changedReloads = world.reloadsChanged
		forgotten = world.chunksForgotten
		misses = world.exactMisses
	}

	private fun seconds(millis: Long): String = "%.1f s".format(millis / 1000.0)

	private companion object {
		const val INTERVAL_MILLIS = 5_000L
	}
}
