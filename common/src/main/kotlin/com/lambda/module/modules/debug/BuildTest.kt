package com.lambda.module.modules.debug

import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.tasks.BuildStructure.Companion.buildStructure
import net.minecraft.block.Blocks

object BuildTest : Module(
    name = "BuildTest",
    description = "Test module for build",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    init {
        onEnable {
            buildStructure {
                player.blockPos
                    .offset(player.horizontalFacing, 2)
                    .toStructure(TargetState.Block(Blocks.NETHERRACK))
                    .toBlueprint()
            }.start(null)
        }
    }
}