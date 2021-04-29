package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.ICancellable
import com.lambda.client.event.ProfilerEvent

class PlayerTravelEvent : Event, ICancellable by Cancellable(), ProfilerEvent {
    override val profilerName: String = "kbPlayerTravel"
}