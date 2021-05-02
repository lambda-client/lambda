package com.lambda.client.module.modules.misc

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRichPresence
import com.lambda.capeapi.CapeType
import com.lambda.client.LambdaMod
import com.lambda.client.event.events.ShutdownEvent
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
import com.lambda.commons.utils.MathUtils
import com.lambda.event.listener.listener
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object DiscordRPC : Module(
    name = "DiscordRPC",
    category = Category.MISC,
    description = "Discord Rich Presence",
    enabledByDefault = false
) {
    private val highwayMode by setting("HighwayMode", false)
    private val line1Left by setting("Line 1 Left", LineInfo.VERSION, { !highwayMode }) // details left
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME, { !highwayMode }) // details right
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION, { !highwayMode }) // state left
    private val line2Right by setting("Line 2 Right", LineInfo.HEALTH, { !highwayMode }) // state right
    private val coordsConfirm by setting("Coords Confirm", false, { showCoordsConfirm() && !highwayMode })

    private enum class LineInfo {
        VERSION, WORLD, DIMENSION, USERNAME, HEALTH, HUNGER, SERVER_IP, COORDS, SPEED, HELD_ITEM, FPS, TPS, NONE
    }

    private val presence = DiscordRichPresence()
    private val rpc = club.minnced.discord.rpc.DiscordRPC.INSTANCE
    private var connected = false
    private val timer = TickTimer(TimeUnit.SECONDS)
    private val job = BackgroundJob("Discord RPC", 5000L) { updateRPC() }

    init {
        onEnable {
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

        listener<ShutdownEvent> {
            end()
        }
    }

    private fun start() {
        if (connected) return

        LambdaMod.LOG.info("Starting Discord RPC")
        connected = true
        rpc.Discord_Initialize(LambdaMod.APP_ID, DiscordEventHandlers(), true, "")
        presence.startTimestamp = System.currentTimeMillis() / 1000L

        BackgroundScope.launchLooping(job)

        LambdaMod.LOG.info("Discord RPC initialised successfully")
    }

    private fun end() {
        if (!connected) return

        LambdaMod.LOG.info("Shutting down Discord RPC...")
        BackgroundScope.cancel(job)
        connected = false
        rpc.Discord_Shutdown()
    }

    private fun showCoordsConfirm(): Boolean {
        return line1Left == LineInfo.COORDS
            || line2Left == LineInfo.COORDS
            || line1Right == LineInfo.COORDS
            || line2Right == LineInfo.COORDS
    }

    private fun updateRPC() {
        if (highwayMode) {
            if (HighwayTools.matPlaced + HighwayTools.netherrackMined > 0) {
                var pre = ""
                when (HighwayTools.mode) {
                    HighwayTools.Mode.HIGHWAY, HighwayTools.Mode.FLAT -> {
                        presence.details = "%,d ${HighwayTools.material.localizedName}".format(HighwayTools.matPlaced)
                        pre = "placed"
                    }
                    HighwayTools.Mode.TUNNEL -> {
                        presence.details = "%,d ${Blocks.NETHERRACK.localizedName}".format(HighwayTools.netherrackMined)
                        pre = "mined"
                    }
                }
                presence.state = "$pre with ${LambdaMod.VERSION_SIMPLE}"
            } else {
                presence.details = "running ${LambdaMod.VERSION_SIMPLE}"
                presence.state = ""
            }
        } else {
            presence.details = getLine(line1Left) + getSeparator(0) + getLine(line1Right)
            presence.state = getLine(line2Left) + getSeparator(1) + getLine(line2Right)
        }
        rpc.Discord_UpdatePresence(presence)
    }

    private fun getLine(line: LineInfo): String {
        return when (line) {
            LineInfo.VERSION -> {
                LambdaMod.VERSION_SIMPLE
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

    fun setCustomIcons(capeType: CapeType?) {
        presence.smallImageKey = capeType?.imageKey ?: ""
        presence.smallImageText = when (capeType) {
            CapeType.BOOSTER -> "booster"
            CapeType.CONTEST -> "contest winner!"
            CapeType.CONTRIBUTOR -> "code contributor!"
            CapeType.DONOR -> "donator <3"
            CapeType.INVITER -> "inviter"
            CapeType.SPECIAL -> "special cape!"
            else -> ""
        }
    }

    init {
        presence.largeImageKey = "kami"
        presence.largeImageText = "kamiblue.org"
    }
}