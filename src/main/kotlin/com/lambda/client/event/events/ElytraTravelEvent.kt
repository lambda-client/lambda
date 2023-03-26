package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.event.IMultiPhase
import com.lambda.client.event.Phase

class ElytraTravelEvent(override val phase: Phase) : Event, IMultiPhase<ElytraTravelEvent> {
    override fun nextPhase(): ElytraTravelEvent {
        throw UnsupportedOperationException()
    }
}