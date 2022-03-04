package com.lambda.client.module.modules.misc

import com.jagrosh.discordipc.IPCClient
import com.jagrosh.discordipc.entities.RichPresence
import com.jagrosh.discordipc.entities.pipe.PipeStatus
import com.jagrosh.discordipc.exceptions.NoDiscordClientException
import com.lambda.client.LambdaMod
import com.lambda.client.capeapi.CapeType
import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.InfoCalculator
import com.lambda.client.util.InfoCalculator.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.TpsCalculator
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.BackgroundJob
import com.lambda.client.util.threads.BackgroundScope
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.time.OffsetDateTime

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence",
    category = Category.MISC
) {
    private val line1Left by setting("Line 1 Left", LineInfo.VERSION) // details left
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME) // details right
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION) // state left
    private val line2Right by setting("Line 2 Right", LineInfo.HEALTH) // state right
    private val coordsConfirm by setting("Coords Confirm", false, { showCoordsConfirm() })

    private enum class LineInfo {
        VERSION, WORLD, DIMENSION, USERNAME, HEALTH, HUNGER, SERVER_IP, COORDS, SPEED, HELD_ITEM, FPS, TPS, NONE
    }

    // Not using "by lazy" to be able to catch failure in onEnable
    private lateinit var ipc: IPCClient
    private var initialised = false
    private val rpcBuilder = RichPresence.Builder()
        .setLargeImage("default", "lambda-client.com")
    private val timer = TickTimer(TimeUnit.SECONDS)
    private val job = BackgroundJob("Discord RPC", 5000L) { updateRPC() }

    init {
        onEnable {
            if (!initialised) {
                try {
                    ipc = IPCClient(LambdaMod.APP_ID)
                    initialised = true
                } catch (e: UnsatisfiedLinkError) {
                    error("Failed to initialise DiscordRPC due to missing native library", e)
                    disable()
                    return@onEnable
                }
            }
            start()
        }

        onDisable {
            end()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (showCoordsConfirm() && !coordsConfirm && timer.tick(10L)) {
                MessageSendHelper.sendWarningMessage("$chatName Warning: In order to use the coords option please enable the coords confirmation option. " +
                    "This will display your coords on the discord rpc. " +
                    "Do NOT use this if you do not want your coords displayed")
            }
        }
    }

    private fun start() {
        LambdaMod.LOG.info("Starting Discord RPC")

        try {
            ipc.connect()
            rpcBuilder.setStartTimestamp(OffsetDateTime.now())
            val richPresence = rpcBuilder.build()
            ipc.sendRichPresence(richPresence)
            BackgroundScope.launchLooping(job)

            LambdaMod.LOG.info("Discord RPC initialised successfully")
        } catch (e: NoDiscordClientException) {
            error("No discord client found for RPC, stopping")
            disable()
        }
    }

    private fun end() {
        LambdaMod.LOG.info("Shutting down Discord RPC...")
        BackgroundScope.cancel(job)
        if (initialised && ipc.status == PipeStatus.CONNECTED) {
            ipc.close()
        }
    }

    private fun showCoordsConfirm(): Boolean {
        return line1Left == LineInfo.COORDS
            || line2Left == LineInfo.COORDS
            || line1Right == LineInfo.COORDS
            || line2Right == LineInfo.COORDS
    }

    private fun updateRPC() {
        when (ipc.status) {
            PipeStatus.CONNECTED -> {
                val richPresence = rpcBuilder
                    .setDetails(getLine(line1Left) + getSeparator(0) + getLine(line1Right))
                    .setState(getLine(line2Left) + getSeparator(1) + getLine(line2Right))
                    .build()
                ipc.sendRichPresence(richPresence)
            }

            PipeStatus.UNINITIALIZED -> {
                tryConnect()
            }

            PipeStatus.DISCONNECTED -> {
                tryConnect()
            }

            else -> {
                // Why is this necessary now kotlin? WHY
            }
        }
    }

    private fun tryConnect() {
        try {
            ipc.connect()
        } catch (e: NoDiscordClientException) {
            // Add something here if you want to spam the log i guess
        }
    }

    private fun getLine(line: LineInfo): String {
        return when (line) {
            LineInfo.VERSION -> {
                LambdaMod.VERSION
            }
            LineInfo.WORLD -> {
                when {
                    mc.isIntegratedServerRunning -> "Singleplayer"
                    mc.currentServerData != null -> "Multiplayer"
                    else -> "Main Menu"
                }
            }
            LineInfo.DIMENSION -> {
                InfoCalculator.dimension()
            }
            LineInfo.USERNAME -> {
                mc.session.username
            }
            LineInfo.HEALTH -> {
                if (mc.player != null) "${mc.player.health.toInt()} HP"
                else "No HP"
            }
            LineInfo.HUNGER -> {
                if (mc.player != null) "${mc.player.foodStats.foodLevel} hunger"
                else "No Hunger"
            }
            LineInfo.SERVER_IP -> {
                InfoCalculator.getServerType()
            }
            LineInfo.COORDS -> {
                if (mc.player != null && coordsConfirm) "(${mc.player.positionVector.toBlockPos().asString()})"
                else "No Coords"
            }
            LineInfo.SPEED -> {
                runSafeR {
                    "${"%.1f".format(speed())} m/s"
                } ?: "No Speed"
            }
            LineInfo.HELD_ITEM -> {
                "Holding ${mc.player?.heldItemMainhand?.displayName ?: "Air"}" // Holding air meme
            }
            LineInfo.FPS -> {
                "${Minecraft.getDebugFPS()} FPS"
            }
            LineInfo.TPS -> {
                if (mc.player != null) "${MathUtils.round(TpsCalculator.tickRate, 1)} tps"
                else "No Tps"
            }
            else -> {
                " "
            }
        }
    }

    private fun getSeparator(line: Int): String {
        return if (line == 0) {
            if (line1Left == LineInfo.NONE || line1Right == LineInfo.NONE) " " else " | "
        } else {
            if (line2Left == LineInfo.NONE || line2Right == LineInfo.NONE) " " else " | "
        }
    }

    // Change to Throwable? if more logging is ever needed
    private fun error(message: String, error: UnsatisfiedLinkError? = null) {
        MessageSendHelper.sendErrorMessage(message)
        LambdaMod.LOG.error(message, error)
    }

    fun setCustomIcons(capeType: CapeType?) {
        // The nullability here is VERY important, DO NOT switch this for an empty string, it causes discord to break
        val text = when (capeType) {
            CapeType.CONTRIBUTOR -> "Contributor"
            else -> null
        }
        rpcBuilder.setSmallImage(capeType?.imageKey, text)
    }
}
