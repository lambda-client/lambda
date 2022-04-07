package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.asyncListener
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.safeListener
import com.mojang.authlib.GameProfile
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap

object LogoutLogger : Module(
    name = "LogoutLogger",
    description = "Logs when a player leaves the game",
    category = Category.MISC
) {
    private val saveWaypoint by setting("Save Waypoint", false)
    private val print by setting("Print To Chat", true)
    private val esp by setting("ESP", true)
    private val espFilledAlpha by setting("ESP Filled Alpha", 47, 0..255, 1, { esp })
    private val espOutlineAlpha by setting("ESP Outline Alpha", 0, 0..255, 1, { esp })
    private val espColor by setting("ESP Color", GuiColors.primary, false, { esp })
    private val clearEsp by setting("Clear ESP", true, { esp })

    private val loggedPlayers = ConcurrentHashMap<GameProfile, EntityOtherPlayerMP>()
    val loggedOutPlayers = ConcurrentHashMap<GameProfile, EntityOtherPlayerMP>()
    private val renderer = ESPRenderer()
    private val renderTimer = TickTimer(TimeUnit.SECONDS)

    init {
        asyncListener<ConnectionEvent.Disconnect> {
            onMainThread {
                loggedPlayers.clear()
                if (clearEsp) loggedOutPlayers.clear()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            world.playerEntities
                .filterIsInstance<EntityOtherPlayerMP>()
                .filter { entityOtherPlayerMP -> !entityOtherPlayerMP.isFakeOrSelf }
                .forEach { entityOtherPlayerMP ->
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    connection.getPlayerInfo(entityOtherPlayerMP.gameProfile.id)?.let { networkPlayerInfo ->
                        loggedPlayers[networkPlayerInfo.gameProfile] = entityOtherPlayerMP

                        loggedOutPlayers.entries.removeIf { (profile, _) ->
                            profile.id == networkPlayerInfo.gameProfile.id
                        }
                    }
                }

            loggedPlayers.entries.removeIf { (profile, entityOtherPlayerMP) ->
                @Suppress("SENSELESS_COMPARISON")
                if (connection.getPlayerInfo(profile.id) == null) {
                    if (saveWaypoint) {
                        WaypointManager.add(entityOtherPlayerMP.entityBoundingBox.center.toBlockPos(), "${profile.name} Logout Spot")
                    }
                    if (print) {
                        MessageSendHelper.sendChatMessage("$chatName ${TextFormatting.RED}${profile.name}${TextFormatting.RESET} logged out at (${entityOtherPlayerMP.entityBoundingBox.center.toBlockPos().asString()})")
                    }

                    // prevent render glitches caused by interpolation
                    entityOtherPlayerMP.prevPosX = entityOtherPlayerMP.posX
                    entityOtherPlayerMP.prevPosY = entityOtherPlayerMP.posY
                    entityOtherPlayerMP.prevPosZ = entityOtherPlayerMP.posZ

                    loggedOutPlayers[profile] = entityOtherPlayerMP
                    true
                } else {
                    false
                }
            }
        }

        onDisable {
            if (clearEsp) loggedOutPlayers.clear()
        }

        listener<RenderWorldEvent> {
            if (!esp) return@listener

            renderer.aFilled = espFilledAlpha
            renderer.aOutline = espOutlineAlpha

            if (renderTimer.tick(1)) {
                renderer.clear()
                loggedOutPlayers.values.forEach {
                    renderer.add(it, espColor)
                }
            }
            renderer.render(false)
        }
    }
}