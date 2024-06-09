package com.lambda.module.modules.debug

import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info

object UpdateTest : Module(
    name = "UpdateTest",
    description = "A module for testing updates",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    init {
        listener<WorldEvent.BlockUpdate> {
            info("Block update at ${it.pos} with state ${it.state} and flags ${it.flags}")
        }

        listener<WorldEvent.ChunkEvent.Load> {
            info("Chunk load at ${it.chunk.pos}")
        }

        listener<WorldEvent.ChunkEvent.Unload> {
            info("Chunk unload at ${it.chunk.pos}")
        }
    }
}