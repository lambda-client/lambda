/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.session.PlanningCancellation
import kotlin.test.Test
import kotlin.test.assertTrue

class PlanningSafetyTest {
    @Test
    fun `planning cancellation is sticky`() {
        val cancellation = PlanningCancellation()

        cancellation.cancel()

        assertTrue(cancellation.isCancelled)
    }

}
