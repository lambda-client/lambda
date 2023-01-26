package com.lambda.client.event.events

import com.lambda.client.event.*

class PushOutOfBlocksEvent : Event, ICancellable by Cancellable()
