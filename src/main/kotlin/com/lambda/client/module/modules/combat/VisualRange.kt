package com.lambda.client.module.modules.combat

import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.text.format
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.common.gameevent.TickEvent

object VisualRange : Module(
    name = "VisualRange",
    description = "Shows players who enter and leave range in chat",
    category = Category.COMBAT,
    alwaysListening = true
) {
    private const val NAME_FORMAT = "\$NAME"

    private val playSound by setting("Play Sound", false)
    private val leaving by setting("Count Leaving", false)
    private val friends by setting("Friends", true)
    private val uwuAura by setting("UwU Aura", false)
    private val logToFile by setting("Log To File", false)
    private val enterMessage by setting("Enter Message", "$NAME_FORMAT spotted!")
    private val leaveMessage by setting("Leave Message", "$NAME_FORMAT left!", { leaving })
    private val publicEnter by setting("Public Message On Enter", false)
    private val publicEnterMessage by setting("Public Enter Message", "Hello $NAME_FORMAT", { publicEnter })

    private val playerSet = LinkedHashSet<EntityPlayer>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END || !timer.tick(1L)) return@safeListener

            val loadedPlayerSet = LinkedHashSet(world.playerEntities)
            for (entityPlayer in loadedPlayerSet) {
                if (entityPlayer.isFakeOrSelf) continue // Self / Freecam / FakePlayer check
                if (!friends && FriendManager.isFriend(entityPlayer.name)) continue // Friend check

                if (playerSet.add(entityPlayer) && isEnabled) {
                    onEnter(entityPlayer)
                }
            }

            val toRemove = ArrayList<EntityPlayer>()
            for (player in playerSet) {
                if (!loadedPlayerSet.contains(player)) {
                    toRemove.add(player)
                    if (isEnabled) onLeave(player)
                }
            }
            playerSet.removeAll(toRemove.toSet())
        }
    }

    private fun onEnter(player: EntityPlayer) {
        val message = enterMessage.replaceName(player)

        sendNotification(message)
        if (logToFile) WaypointManager.add(player.flooredPosition, message)
        val name = player.name
        if (uwuAura) sendServerMessage("/w $name hi uwu")
        if (publicEnter) sendServerMessage(publicEnterMessage.replace(NAME_FORMAT, name))
    }

    private fun onLeave(player: EntityPlayer) {
        if (!leaving) return
        val message = leaveMessage.replaceName(player)

        sendNotification(message)
        if (logToFile) WaypointManager.add(player.flooredPosition, message)
        if (uwuAura) sendServerMessage("/w ${player.name} bye uwu")
    }

    private fun String.replaceName(player: EntityPlayer) = replace(NAME_FORMAT, getColor(player) format player.name)

    private fun getColor(player: EntityPlayer) =
        if (FriendManager.isFriend(player.name)) TextFormatting.GREEN
        else TextFormatting.RED

    private fun sendNotification(message: String) {
        if (playSound) mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
        MessageSendHelper.sendChatMessage(message)
    }
}