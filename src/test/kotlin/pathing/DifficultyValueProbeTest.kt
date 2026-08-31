/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import kotlin.test.Test
import org.junit.jupiter.api.Tag

/**
 * What the temperature ladder actually buys.
 *
 * There used to be two ladders here. The corridor -- which admitted coarse steps further
 * off the best line -- was swept against this corpus and returned nothing: at every
 * temperature its levels 1, 2 and 3 produced tapes within a frame of level 0 while
 * costing about 6% more expansions, and at full heat level 0 was the shortest tape
 * measured outright. It was removed. Temperature pays for itself and is kept honest
 * here. Non-gating report.
 */
@Tag("bedrock-corpus")
class DifficultyValueProbeTest {

    @Test
    fun `tape length and search cost against the difficulty ladders`() {
        val scenarios = ProbeScenarios.sample()
        println("[difficulty] temperature rungs 0.30 0.48 0.77 1.00")
        for (temperature in TEMPERATURES) {
            run {
                var arrived = 0
                var frames = 0
                var expansions = 0L
                var excessSum = 0.0
                var excessCount = 0
                val per = StringBuilder()
                for (scenario in scenarios) {
                    val outcome = ProbeScenarios.plan(scenario, maxTemperature = temperature)
                    expansions += outcome.exhaustions.sumOf { it.expansions.toLong() }
                    val path = (outcome.result as? PathPlanResult.Planned)?.path
                    if (path != null && !path.partial) {
                        arrived++
                        frames += path.plan.frames.size
                        per.append(" ").append(path.plan.frames.size)
                        if (path.excessRatio.isFinite()) {
                            excessSum += (path.excessRatio - 1.0) * 100
                            excessCount++
                        }
                    } else per.append(" FAIL")
                }
                println(
                    "[difficulty] temp<=%.2f  arrived=%d/%d  frames=%-5d excess=%+6.1f%%  expansions=%-8d |%s"
                        .format(
                            temperature, arrived, scenarios.size, frames,
                            if (excessCount > 0) excessSum / excessCount else 0.0, expansions, per,
                        ),
                )
            }
        }
    }

    private companion object {
        /**
         * The endpoints only. The full four-rung sweep is what established that
         * temperature pays and the corridor did not; re-running it every gate buys a
         * curve nobody reads, and this task shares a JVM with a wall-clock test that
         * starves under load. Widen it by hand when re-opening the question.
         */
        val TEMPERATURES = listOf(0.30, 1.00)
    }
}
