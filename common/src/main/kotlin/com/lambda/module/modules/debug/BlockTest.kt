package com.lambda.module.modules.debug

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.blockSearch
import net.minecraft.block.Blocks
import net.minecraft.util.math.Vec3i
import java.awt.Color

object BlockTest : Module(
    name = "BlockTest",
    description = "BlockTest",
    defaultTags = setOf(ModuleTag.DEBUG),
) {
    private val rangeX by setting("Range X", 5, 1..7, 1, "Range X")
    private val rangeY by setting("Range Y", 5, 1..7, 1, "Range Y")
    private val rangeZ by setting("Range Z", 5, 1..7, 1, "Range Z")
    private val stepX by setting("Step X", 1, 1..7, 1, "Step X")
    private val stepY by setting("Step Y", 1, 1..7, 1, "Step Y")
    private val stepZ by setting("Step Z", 1, 1..7, 1, "Step Z")

    private val range: Vec3i
        get() = Vec3i(rangeX, rangeY, rangeZ)

    private val step: Vec3i
        get() = Vec3i(stepX, stepY, stepZ)

    private val filledColor = Color(100, 150, 255, 128)
    private val outlineColor = Color(100, 150, 255, 51)

    init {
        listener<RenderEvent.StaticESP> {
            blockSearch {
                range(range)
                step(step)

                filter { _, block -> block.isOf(Blocks.DIAMOND_BLOCK) }
                iterator { pos, state ->
                    state.getOutlineShape(world, pos).boundingBoxes.forEach { box ->
                        it.renderer.build(box.offset(pos), filledColor, outlineColor)
                    }
                }
            }.build()
        }
    }
}
