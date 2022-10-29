package com.lambda.client.module.modules.chat

import com.lambda.client.LambdaMod
import com.lambda.client.commons.extension.synchronized
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Items
import net.minecraft.network.play.server.SPacketUpdateHealth
import net.minecraft.util.EnumHand
import java.io.File
import kotlin.random.Random

object AutoExcuse : Module(
    name = "AutoExcuse",
    description = "Makes an excuse for you when you die",
    category = Category.CHAT,
    modulePriority = 500
) {
    private val file = File(FolderUtils.lambdaFolder + "excuses.txt")
    private val spammer = ArrayList<String>().synchronized()
    private var currentLine = 0

    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<PacketEvent.Receive> {
            if (spammer.isEmpty() || it.packet !is SPacketUpdateHealth) return@safeListener
            if (it.packet.health <= 0.0f && !isHoldingTotem && timer.tick(3L)) {
                sendServerMessage(getExcuse())
            }
        }

        onEnable {
            if (file.exists()) {
                try {
                    file.forEachLine { if (it.isNotBlank()) spammer.add(it.trim()) }
                    MessageSendHelper.sendChatMessage("$chatName Loaded spammer messages!")
                } catch (e: Exception) {
                    LambdaMod.LOG.error("Failed loading excuses", e)
                }
            } else {
                file.createNewFile()
                MessageSendHelper.sendErrorMessage("$chatName Excuses file is empty!" +
                    ", please add them in the &7excuses.txt&f under the &7.minecraft/lambda&f directory.")
            }
        }
    }

    private val SafeClientEvent.isHoldingTotem: Boolean
        get() = EnumHand.values().any { player.getHeldItem(it).item == Items.TOTEM_OF_UNDYING }

    private fun getExcuse(): String {
        val prevLine = currentLine
        // Avoids sending the same message
        while (spammer.size != 1 && currentLine == prevLine) {
            currentLine = Random.nextInt(spammer.size)
        }
        return spammer[currentLine]
    }
}
