package com.lambda.client.module.modules.misc

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

object TeleportLogger : Module(
    name = "TeleportLogger",
    description = "Logs when a player teleports somewhere",
    category = Category.MISC
) {
    private val saveToWaypoints by setting("Save To Waypoints", true)
    private val remove by setting("Remove In Range", true)
    private val printAdd by setting("Print Add", true)
    private val printRemove by setting("Print Remove", true, { remove })
    private val minimumDistance by setting("Minimum Distance", 512, 128..2048, 128)

    private val teleportedPlayers = HashMap<String, BlockPos>()

    init {
        safeListener<TickEvent.ClientTickEvent> {
            for (worldPlayer in world.playerEntities) {
                if (worldPlayer.isFakeOrSelf) continue

                /* 8 chunk render distance * 16 */
                if (remove && worldPlayer.getDistance(player) < 128) {
                    if (teleportedPlayers.contains(worldPlayer.name)) {
                        val removed = WaypointManager.remove(teleportedPlayers[worldPlayer.name]!!)
                        teleportedPlayers.remove(worldPlayer.name)

                        if (removed) {
                            if (printRemove) MessageSendHelper.sendChatMessage("$chatName Removed ${worldPlayer.name}, they are now ${MathUtils.round(worldPlayer.getDistance(mc.player), 1)} blocks away")
                        } else {
                            if (printRemove) MessageSendHelper.sendErrorMessage("$chatName Error removing ${worldPlayer.name} from coords, their position wasn't saved anymore")
                        }
                    }
                    continue
                }

                if (worldPlayer.getDistance(player) < minimumDistance || teleportedPlayers.containsKey(worldPlayer.name)) {
                    continue
                }

                val coords = logCoordinates(worldPlayer.position, "${worldPlayer.name} Teleport Spot")
                teleportedPlayers[worldPlayer.name] = coords
                if (printAdd) MessageSendHelper.sendChatMessage("$chatName ${worldPlayer.name} teleported, ${getSaveText()} ${coords.x}, ${coords.y}, ${coords.z}")
            }
        }
    }

    private fun logCoordinates(coordinate: BlockPos, name: String): BlockPos {
        return if (saveToWaypoints) WaypointManager.add(coordinate, name).pos
        else coordinate
    }

    private fun getSaveText(): String {
        return if (saveToWaypoints) "saved their coordinates at"
        else "their coordinates are"
    }
}
