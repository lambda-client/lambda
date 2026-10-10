/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.session.PathPlanResult
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * A body that adopts publications late must not strand the search. The search used to
 * presume a publication refused after 150 ms without adoption and drop it; when the body
 * adopted it anyway, the search measured divergence against the tape before it, published
 * extensions of a tape the body never ran and saw every one refused while its open set
 * grew -- a field journey held at a tape terminal for 30 minutes until cancelled. The body
 * now reports each refusal, so the search never guesses one.
 */
class SlowAdoptionTest {
	@Test
	@Timeout(120)
	fun `publications that reach the body late still carry it to the goal`() {
		val scenario = ProbeScenarios.all().first { it.name == "bedrock-traverse" }
		for (latency in listOf(4, 8)) {
			val planned = assertIs<PathPlanResult.Planned>(ProbeScenarios.plan(scenario, latencyTicks = latency).result, "latency $latency ticks")
			assertFalse(planned.path.partial, "latency $latency ticks must reach the goal")
		}
	}
}
