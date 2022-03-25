package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.asyncListener
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.safeListener
import com.mojang.authlib.GameProfile
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

object LogoutLogger : Module(
    name = "LogoutLogger",
    description = "Logs when a player leaves the game",
    category = Category.MISC
) {
    private val saveWaypoint by setting("Save Waypoint", true)
    private val print by setting("Print To Chat", true)
    private val esp by setting("ESP", true)
    private val espFilledAlpha by setting("ESP Filled Alpha", 47, 0..255, 1, { esp })
    private val espOutlineAlpha by setting("ESP Outline Alpha", 0, 0..255, 1, { esp })
    private val espColor by setting("ESP Color", GuiColors.primary, false, { esp })
    private val clearEsp by setting("Disable Clear ESP", false, { esp })

    private val loggedPlayers = LinkedHashMap<GameProfile, BlockPos>()
    private val timer = TickTimer(TimeUnit.SECONDS)
    private val renderer = ESPRenderer()
    private val renderTimer = TickTimer(TimeUnit.SECONDS)
    private val loggedOutPlayers = mutableMapOf<GameProfile, AxisAlignedBB>()
    
    init {
        asyncListener<ConnectionEvent.Disconnect> {
            onMainThread {
                loggedPlayers.clear()
                loggedOutPlayers.clear()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            for (loadedPlayer in world.playerEntities) {
                if (loadedPlayer !is EntityOtherPlayerMP) continue
                if (loadedPlayer.isFakeOrSelf) continue

                val info = connection.getPlayerInfo(loadedPlayer.gameProfile.id) ?: continue
                loggedPlayers[info.gameProfile] = loadedPlayer.flooredPosition
                for ((profile, _) in loggedOutPlayers) {
                    if (profile.id == info.gameProfile.id) loggedOutPlayers.remove(profile)
                }
            }

            if (timer.tick(1L)) {
                val toRemove = ArrayList<GameProfile>()

                loggedPlayers.entries.removeIf { (profile, pos) ->
                    @Suppress("SENSELESS_COMPARISON")
                    if (connection.getPlayerInfo(profile.id) == null) {
                        if (saveWaypoint) WaypointManager.add(pos, "${profile.name} Logout Spot")
                        if (print) MessageSendHelper.sendChatMessage("${profile.name} logged out at ${pos.asString()}")
                        val aabb = AxisAlignedBB(pos)
                        loggedOutPlayers[profile] = aabb.setMaxY(aabb.maxY + 1)
                        true
                    } else {
                        false
                    }
                }

                loggedPlayers.keys.removeAll(toRemove.toSet())
            }
        }
        
        onDisable { 
            if (clearEsp) loggedOutPlayers.clear()
        }
        
        listener<RenderWorldEvent> { 
            if (!esp) return@listener
            renderer.aFilled = espFilledAlpha
            renderer.aOutline = espOutlineAlpha
            val shouldUpdate = renderTimer.tick(3)
            if (shouldUpdate) {
                renderer.clear()
                for ((_, aabb) in loggedOutPlayers) {
                    renderer.add(aabb, espColor)
                }
            }
            renderer.render(false)
        }
    }
}