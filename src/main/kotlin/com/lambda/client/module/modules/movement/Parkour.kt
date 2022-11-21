package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.module.modules.render.Search.setting
import com.lambda.client.util.Wrapper
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.client.event.InputUpdateEvent

object Parkour: Module(
    name = "Parkour",
    category = Category.MOVEMENT,
    description = "Jumps when you are at the edge of a block"
) {
    private val autoJump by setting("Auto Jump", true)
    private val onlyOnForwardKey by setting("Only on Forward", true, { autoJump })
    private val onSneak by setting("On Sneak", false)
    private val distanceToEdge by setting("Distance to edge", 0.001, 0.001..1.0, 0.001, { autoJump }, unit = " blocks")
    private val distanceToFloor by setting("Distance to floor", 0.07, 0.01..0.6, 0.01, { autoJump }, unit = " blocks")
    private val esp by setting("FeetESP", true)
    private val espFilledAlpha by setting("ESP Filled Alpha", 47, 0..255, 1, { esp })
    private val espOutlineAlpha by setting("ESP Outline Alpha", 0, 0..255, 1, { esp })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f, { esp })
    private val espColor by setting("ESP Color", GuiColors.primary, false, { esp })

    private val renderer = ESPRenderer()
    private var currentCheckAABB = AxisAlignedBB(BlockPos.ORIGIN)

    init {
        safeListener<InputUpdateEvent> {
            currentCheckAABB = player.entityBoundingBox
                .offset(0.0, -distanceToFloor, 0.0)
                .grow(-distanceToEdge, 0.0, -distanceToEdge)

            if (autoJump
                && player.onGround
                && world.getCollisionBoxes(player, currentCheckAABB).isEmpty()
                && (!player.isSneaking || !onSneak)
                && (it.movementInput.forwardKeyDown || !onlyOnForwardKey)
            ) it.movementInput.jump = true
        }

        listener<RenderWorldEvent> {
            if (!esp) return@listener

            renderer.aFilled = espFilledAlpha
            renderer.aOutline = espOutlineAlpha
            renderer.thickness = thickness

            renderer.clear()
            Wrapper.player?.let {
                renderer.add(it, espColor, GeometryMasks.Quad.DOWN)
            }

            renderer.render(false)
        }
    }
}