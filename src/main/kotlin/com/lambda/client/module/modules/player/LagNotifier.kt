package com.lambda.client.module.modules.player

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.NetworkManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.process.PauseProcess.pauseBaritone
import com.lambda.client.process.PauseProcess.unpauseBaritone
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.glColor4f

/**
 * Thanks Brady and cooker and leij for helping me not be completely retarded
 */
object LagNotifier : Module(
    name = "LagNotifier",
    description = "Displays a warning when the server is lagging",
    category = Category.PLAYER
) {
    private val detectRubberBand by setting("Detect Rubber Band", true)
    private val pauseBaritone by setting("Pause Baritone", true)
    val pauseTakeoff by setting("Pause Elytra Takeoff", true)
    val pauseAutoWalk by setting("Pause Auto Walk", true)
    private val feedback by setting("Pause Feedback", true, { pauseBaritone })
    private val timeout by setting("Timeout", 3.5f, 0.0f..10.0f, 0.5f)

    private val lastPacketTimer = TickTimer()
    private val lastRubberBandTimer = TickTimer()
    private var text = ""

    var isBaritonePaused = false; private set

    init {
        onDisable {
            requestUnpause()
        }

        listener<RenderOverlayEvent> {
            if (text.isBlank()) return@listener

            val resolution = ScaledResolution(mc)
            val posX = resolution.scaledWidth / 2.0f - FontRenderAdapter.getStringWidth(text) / 2.0f
            val posY = 80.0f / resolution.scaleFactor

            /* 80px down from the top edge of the screen */
            FontRenderAdapter.drawString(text, posX, posY, color = ColorHolder(255, 33, 33))
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mc.isIntegratedServerRunning) {
                requestUnpause()
                text = ""
            } else {
                val timeoutMillis = (timeout * 1000.0f).toLong()
                when {
                    lastPacketTimer.tick(timeoutMillis, false) -> {
                        text = if (NetworkManager.isOffline) "Your internet is offline! "
                        else "Server Not Responding! "

                        text += timeDifference(lastPacketTimer.time)
                        requestPause()
                    }
                    detectRubberBand && !lastRubberBandTimer.tick(timeoutMillis, false) -> {
                        text = "RubberBand Detected! ${timeDifference(lastRubberBandTimer.time)}"
                        requestPause()
                    }
                    else -> {
                        requestUnpause()
                    }
                }
            }
        }

        safeListener<PacketEvent.Receive>(2000) {
            lastPacketTimer.reset()

            if (!detectRubberBand || it.packet !is SPacketPlayerPosLook || player.ticksExisted < 20) return@safeListener

            val dist = Vec3d(it.packet.x, it.packet.y, it.packet.z).subtract(player.positionVector).length()
            val rotationDiff = Vec2f(it.packet.yaw, it.packet.pitch).minus(Vec2f(player)).length()

            if (dist in 0.5..64.0 || rotationDiff > 1.0) lastRubberBandTimer.reset()
        }

        listener<ConnectionEvent.Connect> {
            lastPacketTimer.reset(69420L)
            lastRubberBandTimer.reset(-69420L)
        }
    }

    private fun requestPause() {
        if (!isBaritonePaused && pauseBaritone && feedback) {
            MessageSendHelper.sendBaritoneMessage("Paused due to lag!")
        }

        pauseBaritone()
        isBaritonePaused = true
    }

    private fun requestUnpause() {
        if (isBaritonePaused && pauseBaritone && feedback) {
            MessageSendHelper.sendBaritoneMessage("Unpaused!")
        }

        unpauseBaritone()
        isBaritonePaused = false
        text = ""
    }

    private fun timeDifference(timeIn: Long) = MathUtils.round((System.currentTimeMillis() - timeIn) / 1000.0, 1)
}
