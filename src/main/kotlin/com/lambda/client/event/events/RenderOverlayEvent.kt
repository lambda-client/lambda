package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.event.ProfilerEvent

class RenderOverlayEvent : Event, ProfilerEvent {
    override val profilerName: String = "kbRender2D"
}