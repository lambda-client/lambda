package com.lambda.client.module.modules.client

import com.lambda.client.event.events.BaritoneSettingsInitEvent
import com.lambda.client.event.events.RenderRadarEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.safeListener
import com.lambda.client.event.listener.listener
import net.minecraft.util.math.BlockPos

object Baritone : Module(
    name = "Baritone",
    category = Category.CLIENT,
    description = "Configures Baritone settings",
    showOnArray = false,
    alwaysEnabled = true
) {
    private val showOnRadar by setting("Show Path on Radar", true, description = "Show the current path on radar.")
    private val color by setting("Path Color", ColorHolder(32, 250, 32), visibility = { showOnRadar })
    private val allowBreak by setting("Allow Break", true)
    private val allowSprint by setting("Allow Sprint", true)
    private val allowPlace by setting("Allow Place", true)
    private val allowInventory by setting("Allow Inventory", false)
    private val freeLook by setting("Free Look", true)
    private val allowDownwardTunneling by setting("Downward Tunneling", true)
    private val allowParkour by setting("Allow Parkour", true)
    private val allowParkourPlace by setting("Allow Parkour Place", true)
    private val avoidPortals by setting("Avoid Portals", false)
    private val renderGoal by setting("Render Goals", true)
    private val failureTimeout by setting("Fail Timeout", 2, 1..20, 1)
    private val blockReachDistance by setting("Reach Distance", 4.5f, 1.0f..10.0f, 0.5f)

    init {
        settingList.forEach {
            it.listeners.add { sync() }
        }

        listener<BaritoneSettingsInitEvent> {
            sync()
        }

        safeListener<RenderRadarEvent> {
            if (!showOnRadar || !BaritoneUtils.isPathing) return@safeListener

            val path = BaritoneUtils.primary?.pathingBehavior?.path ?: return@safeListener

            if (!path.isPresent) return@safeListener

            val playerOffset = Vec2d(player.position.x.toDouble(), player.position.z.toDouble())

            for (movement in path.get().movements()) {
                val positionFrom = getPos(movement.src, playerOffset, it.scale)
                val positionTo = getPos(movement.dest, playerOffset, it.scale)

                if (positionFrom.length() < it.radius && positionTo.length() < it.radius) {
                    RenderUtils2D.drawLine(it.vertexHelper, positionFrom, positionTo, color = color, lineWidth = 3f)
                }
            }
        }
    }

    private fun getPos(blockPos: BlockPos, playerOffset: Vec2d, scale: Float): Vec2d {
        return Vec2d(blockPos.x.toDouble(), blockPos.z.toDouble()).minus(playerOffset).div(scale.toDouble())
    }

    private fun sync() {
        BaritoneUtils.settings?.let {
            it.chatControl.value = false // enable chatControlAnyway if you want to use it
            it.allowBreak.value = allowBreak
            it.allowSprint.value = allowSprint
            it.allowPlace.value = allowPlace
            it.allowInventory.value = allowInventory
            it.freeLook.value = freeLook
            it.allowDownward.value = allowDownwardTunneling
            it.allowParkour.value = allowParkour
            it.allowParkourPlace.value = allowParkourPlace
            it.enterPortal.value = !avoidPortals
            it.renderGoal.value = renderGoal
            it.failureTimeoutMS.value = failureTimeout * 1000L
            it.blockReachDistance.value = blockReachDistance
        }
    }
}
