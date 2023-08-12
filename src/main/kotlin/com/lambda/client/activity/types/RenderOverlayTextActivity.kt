package com.lambda.client.activity.types

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.module.modules.client.BuildTools.maxDebugAmount
import com.lambda.client.module.modules.client.BuildTools.maxDebugRange
import com.lambda.client.module.modules.client.BuildTools.showDebugRender
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.util.math.Vec3d

interface RenderOverlayTextActivity {
    val overlayTexts: MutableSet<RenderOverlayText>

    companion object {
        val normalizedRender: MutableSet<RenderOverlayText> = mutableSetOf()

        fun SafeClientEvent.checkOverlayRender() {
            if (!showDebugRender) return

            normalizedRender.clear()

            ActivityManager
                .allSubActivities
                .filterIsInstance<RenderOverlayTextActivity>()
                .forEach { activity ->
                    activity.overlayTexts
                        .sortedBy { player.distanceTo(it.origin) }
                        .filter { player.distanceTo(it.origin) < maxDebugRange }
                        .take(maxDebugAmount)
                        .forEach { compound ->
                            normalizedRender.add(compound)
                        }
                }
        }

        interface RenderOverlayTextCompound

        data class RenderOverlayText(
            var text: String,
            var color: ColorHolder = ColorHolder(255, 255, 255),
            var origin: Vec3d,
            var index: Int = 0,
            var scale: Float = 1f,
        ) : RenderOverlayTextCompound
    }
}