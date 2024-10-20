package com.lambda.module.modules.player

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BaritoneUtils
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color

object WorldEater : Module(
    name = "WorldEater",
    description = "Eats the world",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
//    private val height by setting("Height", 4, 1..10, 1)
//    private val width by setting("Width", 6, 1..30, 1)
    private val pos1 by setting("Position 1", BlockPos(351, 104, 103))
    private val pos2 by setting("Position 2", BlockPos(361, 70, 113))
    private val layerSize by setting("Layer Size", 1, 1..10, 1)
    private var runningTask: Task<*>? = null
    private var area = BlockBox.create(pos1, pos2)
    private val work = mutableListOf<BlockBox>()

    init {
        onEnable {
            area = BlockBox.create(pos1, pos2)
            val layerRanges = (area.minY..area.maxY step layerSize).reversed()
            work.addAll(layerRanges.mapNotNull { y ->
                if (y == area.minY) return@mapNotNull null
                BlockBox(area.minX, y - layerSize, area.minZ, area.maxX, y, area.maxZ)
            })

            buildLayer()
        }

        onDisable {
            runningTask?.cancel()
            runningTask = null
            work.clear()
            BaritoneUtils.cancel()
        }

        listener<RenderEvent.StaticESP> {
            it.renderer.buildOutline(Box.enclosing(pos1, pos2), Color.BLUE)
        }
    }

    private fun buildLayer() {
        work.firstOrNull()?.let { box ->
            runningTask = build {
                box.toStructure(TargetState.Air)
                    .toBlueprint()
            }.onSuccess { _, _ ->
                work.removeFirstOrNull()
                buildLayer()
            }.start(null)
        } ?: disable()
    }
}