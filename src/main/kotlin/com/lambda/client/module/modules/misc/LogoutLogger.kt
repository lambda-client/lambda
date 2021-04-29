package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.safeListener
import com.lambda.event.listener.asyncListener
import com.mojang.authlib.GameProfile
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object LogoutLogger : Module(
    name = "LogoutLogger",
    category = Category.MISC,
    description = "Logs when a player leaves the game"
) {
    private val saveWaypoint by setting("Save Waypoint", true)
    private val print by setting("Print To Chat", true)

    private val loggedPlayers = LinkedHashMap<GameProfile, BlockPos>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        asyncListener<ConnectionEvent.Disconnect> {
            onMainThread {
                loggedPlayers.clear()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            for (loadedPlayer in world.playerEntities) {
                if (loadedPlayer !is EntityOtherPlayerMP) continue
                if (loadedPlayer.isFakeOrSelf) continue

                val info = connection.getPlayerInfo(loadedPlayer.gameProfile.id) ?: continue
                loggedPlayers[info.gameProfile] = loadedPlayer.flooredPosition
            }

            if (timer.tick(1L)) {
                val toRemove = ArrayList<GameProfile>()

                loggedPlayers.entries.removeIf { (profile, pos) ->
                    @Suppress("SENSELESS_COMPARISON")
                    if (connection.getPlayerInfo(profile.id) == null) {
                        if (saveWaypoint) WaypointManager.add(pos, "${profile.name} Logout Spot")
                        if (print) MessageSendHelper.sendChatMessage("${profile.name} logged out at ${pos.asString()}")
                        true
                    } else {
                        false
                    }
                }

                loggedPlayers.keys.removeAll(toRemove)
            }
        }
    }
}