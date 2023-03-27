package com.lambda.client.activity.types

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.util.color.ColorHolder
import net.minecraft.util.math.Vec3d

interface RenderOverlayTextActivity {
    val overlayTexts: MutableSet<RenderOverlayText>

    companion object {
        val normalizedRender: MutableSet<RenderOverlayText> = mutableSetOf()

        fun SafeClientEvent.checkOverlayRender() {
            normalizedRender.clear()

            ActivityManager
                .allSubActivities
                .filterIsInstance<RenderOverlayTextActivity>()
                .forEach { activity ->
                    activity.overlayTexts.forEach { compound ->
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