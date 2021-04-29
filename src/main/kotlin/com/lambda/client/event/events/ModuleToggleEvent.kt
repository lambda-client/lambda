package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.module.AbstractModule

class ModuleToggleEvent internal constructor(val module: AbstractModule) : Event