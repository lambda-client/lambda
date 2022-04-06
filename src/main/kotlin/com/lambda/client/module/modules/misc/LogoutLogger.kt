package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.asyncListener
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.module.modules.render.Nametags
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.safeListener
import com.mojang.authlib.GameProfile
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
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

            for (loadedPlayer in world.playerEntities) {
                if (loadedPlayer !is EntityOtherPlayerMP) continue
                if (loadedPlayer.isFakeOrSelf) continue

                val info = connection.getPlayerInfo(loadedPlayer.gameProfile.id) ?: continue
                loggedPlayers[info.gameProfile] = loadedPlayer

                loggedOutPlayers.entries.removeIf { (profile, _) ->
                    profile.id == info.gameProfile.id
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

                    entityOtherPlayerMP.setNoGravity(true) // try to fix glitching nametags
                    entityOtherPlayerMP.setVelocity(.0, .0, .0) // try to fix glitching nametags

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

            if (renderTimer.tick(3)) {
                renderer.clear()
                loggedOutPlayers.values.forEach {
                    renderer.add(it, espColor)
                }
            }
            renderer.render(false)
        }
    }
}