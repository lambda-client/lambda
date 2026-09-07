package pathing

import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.VirtualSearchClock

/**
 * The half of the walk the corpus harness was missing: a body that can refuse a tape.
 *
 * [com.lambda.pathing.TrajectoryPlanner.walkHorizon] defaults `adoptedSequence` to
 * `Long.MAX_VALUE`, so every fixture built on it modelled an executor that accepts every
 * publication, instantly, and never refuses one. Real walks do not behave that way -- a
 * logged parkour run published seven tapes and adopted five -- and the difference is not
 * cosmetic. An unacknowledged publication blocks `publishPrefix` and
 * `commitFromCandidates` behind `ackedUpToDate()` until the rollback timer fires, and the
 * body keeps eating frames throughout. With the refusal path absent the corpus scored a
 * 3% stall rate against roughly 75% in game, which made it useless for exactly the
 * failure it was being asked about.
 *
 * So this runs the real [ImprovementArbiter] against a cursor that advances on its own
 * schedule, and makes a publication visible only after [latencyTicks] -- the round trip a
 * real publication takes from the planner thread through `mc.execute` to the next client
 * tick.
 */
internal class VirtualExecutor(
    private val clock: VirtualSearchClock,
    private val latencyTicks: Int = ADOPTION_LATENCY_TICKS,
) {
    private class Offer(val visibleAt: Int, val path: PublishedPath)

    private val queued = ArrayDeque<Offer>()
    private var running: PublishedPath? = null
    private var replayBeganAt: Int? = null
    private var acked = 0L

    var adoptions = 0
        private set

    var refusals = 0
        private set

    val refusalReasons = HashMap<String, Int>()

    /** Frames the body has replayed, or null while it is still standing at the start. */
    val executedFrames: Int get() = cursorFrame() ?: 0

    /** The search publishes here. Nothing is visible to the body until the latency passes. */
    fun offer(path: PublishedPath) {
        queued += Offer(clock.cursorFrame() + latencyTicks, path)
    }

    fun adoptedSequence(): Long = acked

    /**
     * The frame the body is replaying.
     *
     * Null until the first tape is installed: a body with no tape is standing still, and
     * reporting a cursor for it would have the search believe frames were being consumed
     * before any existed. Replay starts from zero at the moment of installation, so the
     * planning time before it costs the body nothing.
     */
    fun cursorFrame(): Int? {
        val now = clock.cursorFrame()
        while (queued.isNotEmpty() && queued.first().visibleAt <= now) {
            deliver(queued.removeFirst().path, now)
        }
        val tape = running ?: return null
        val began = replayBeganAt ?: return null
        return (now - began).coerceIn(0, tape.plan.tape.frameCount)
    }

    private fun deliver(path: PublishedPath, now: Int) {
        val current = running
        val cursor = current?.let { (now - (replayBeganAt ?: now)).coerceIn(0, it.plan.tape.frameCount) }
        when (val verdict = ImprovementArbiter.judge(
            running = current,
            cursorFrame = cursor,
            awaitingObservation = false,
            offered = path,
        )) {
            ImprovementArbiter.Verdict.BeginFresh -> {
                running = path
                replayBeganAt = now
                acked = path.publicationSequence.toLong()
                adoptions++
            }

            is ImprovementArbiter.Verdict.Adopt -> {
                running = path
                acked = path.publicationSequence.toLong()
                adoptions++
            }

            // A deferral is a refusal for one tick in the real manager, which retries it
            // on the next. Counting it as a refusal here overstates nothing: the search
            // is blocked on the acknowledgement either way.
            is ImprovementArbiter.Verdict.Keep -> {
                refusals++
                refusalReasons.merge((verdict as ImprovementArbiter.Verdict.Keep).reason, 1, Int::plus)
            }

            ImprovementArbiter.Verdict.DeferForObservation -> refusals++
        }
    }

    private companion object {
        /**
         * Ticks between a publication and the body seeing it.
         *
         * The planner publishes off-thread and the manager adopts inside `mc.execute`, so
         * the earliest a tape can be installed is the following client tick.
         */
        const val ADOPTION_LATENCY_TICKS = 1
    }
}
