/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.maneuver.EntrySpeedEnvelope
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntrySpeedEnvelopeTest {
    @Test
    fun `contains honors interval and tolerance`() {
        val envelope = EntrySpeedEnvelope(0.21, 0.27, maxTakeoffProgress = 0.15)
        assertTrue(envelope.contains(0.21))
        assertTrue(envelope.contains(0.27))
        assertFalse(envelope.contains(0.209))
        assertFalse(envelope.contains(0.271))
        assertTrue(envelope.contains(0.209, tolerance = 0.008))
        assertTrue(envelope.contains(0.278, tolerance = 0.008))
        assertFalse(envelope.contains(0.28, tolerance = 0.008))
    }

    @Test
    fun `single sample interval is legitimate`() {
        // A maximum-distance momentum jump can validate at exactly one
        // sampled speed; the envelope must represent it.
        val envelope = EntrySpeedEnvelope(0.30, 0.30, maxTakeoffProgress = 0.0)
        assertTrue(envelope.contains(0.30))
        assertFalse(envelope.contains(0.29))
    }

    @Test
    fun `invalid intervals are rejected`() {
        assertFailsWith<IllegalArgumentException> { EntrySpeedEnvelope(0.3, 0.2) }
        assertFailsWith<IllegalArgumentException> { EntrySpeedEnvelope(-0.1, 0.2) }
        assertFailsWith<IllegalArgumentException> { EntrySpeedEnvelope(0.1, 0.2, maxTakeoffProgress = -0.1) }
    }
}
