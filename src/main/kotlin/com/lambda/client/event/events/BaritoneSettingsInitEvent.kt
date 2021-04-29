package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.event.KamiEventBus
import com.lambda.client.event.SingletonEvent

/**
 * Posted at the return of when Baritone's Settings are initialized.
 */
object BaritoneSettingsInitEvent : Event, SingletonEvent(KamiEventBus)