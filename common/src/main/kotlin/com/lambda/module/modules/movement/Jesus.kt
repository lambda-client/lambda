package com.lambda.module.modules.movement

import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.Blocks
import net.minecraft.util.shape.VoxelShapes

object Jesus : Module(
    name = "Jesus",
    description = "Allows to walk on water",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val waterState by lazy { Blocks.WATER.defaultState }
    private val fullShape = VoxelShapes.fullCube()

    init {
        listener<WorldEvent.Collision> { event ->
            if (event.state == waterState) {
                event.shape = fullShape
            }
        }
    }
}