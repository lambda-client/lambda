package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SingletonEvent

object ShutdownEvent : Event, SingletonEvent(LambdaEventBus)