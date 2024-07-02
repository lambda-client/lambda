package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.event.EventFlow
import com.lambda.module.ModuleRegistry
import com.lambda.util.Formatting.asString
import com.lambda.util.primitives.extension.tickDelta
import net.minecraft.util.Formatting
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult

object DebugInfoHud {
    @JvmStatic
    fun MutableList<String>.addDebugInfo() {
        add("")
        add("" + Formatting.UNDERLINE + "Lambda ${Lambda.VERSION}+${mc.versionType}")
        add("Modules: ${ModuleRegistry.modules.size} with ${ModuleRegistry.modules.sumOf { it.settings.size }} settings")
        add("Commands: ${CommandRegistry.commands.size}")
        add("Synchronous Listeners: ${EventFlow.syncListeners.size}")
        add("Concurrent Listeners: ${EventFlow.concurrentListeners.size}")

        when (val hit = mc.crosshairTarget) {
            is BlockHitResult -> {
                add("Crosshair Target: Block")
                add("  Vec3d: %.5f, %.5f, %.5f".format(hit.pos.x, hit.pos.y, hit.pos.z))
                add("  BlockPos: ${hit.blockPos.toShortString()}")
                add("  Side: ${hit.side}")
            }

            is EntityHitResult -> {
                add("Crosshair Target: Entity")
                add("  Vec3d: ${hit.pos}")
                add("  Entity: ${hit.entity}")
            }

            null -> add("Crosshair Target: None")
        }

        add("Eye Pos: ${mc.cameraEntity?.getCameraPosVec(mc.tickDelta)?.asString(3)}")

        return
    }
}
