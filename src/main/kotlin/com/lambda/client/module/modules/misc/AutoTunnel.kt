package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.BaritoneCommandEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.movement.AutoWalk
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.round

object AutoTunnel : Module(
    name = "AutoTunnel",
    description = "Automatically tunnels forward, at a given size",
    category = Category.MISC
) {
    private val backFill = setting("Back Fill", false)
    private val height = setting("Height", 2, 1..10, 1)
    private val width = setting("Width", 1, 1..10, 1)
    private val disableOnDisconnect = setting("Disable On Disconnect", true)

    private var lastDirection = EnumFacing.NORTH

    override fun isActive(): Boolean {
        return isEnabled
            && (BaritoneUtils.isPathing
            || BaritoneUtils.primary?.builderProcess?.isActive == true)
    }

    init {
        onDisable {
            BaritoneUtils.cancelEverything()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (!isActive()) sendTunnel()
        }

        listener<BaritoneCommandEvent> {
            if (it.command.contains("cancel")) {
                disable()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            if (disableOnDisconnect.value) disable()
        }
    }

    private fun sendTunnel() {
        mc.player?.let {
            if (AutoWalk.isEnabled) AutoWalk.disable()
            BaritoneUtils.cancelEverything()
            val normalizedYaw = RotationUtils.normalizeAngle(it.rotationYaw)
            it.rotationYaw = round(normalizedYaw / 90.0f) * 90.0f
            it.rotationPitch = 0.0f
            lastDirection = it.horizontalFacing

            MessageSendHelper.sendBaritoneCommand("tunnel", height.value.toString(), width.value.toString(), "100")
        }
    }

    override fun getHudInfo(): String {
        return lastDirection.name2.capitalize()
    }

    init {
        with({ if (mc.player != null && isEnabled) sendTunnel() }) {
            height.listeners.add(this)
            width.listeners.add(this)
        }

        backFill.listeners.add { BaritoneUtils.settings?.backfill?.value = backFill.value }
    }
}