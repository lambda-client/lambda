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
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val modeSetting by setting("Order", Mode.RANDOM_ORDER)
    private val modeSource by setting("Source", SourceMode.INTERNAL)

    private val file = File(FolderUtils.lambdaFolder + "excuses.txt")
    private val loadedExcuses = ArrayList<String>().synchronized()
    private var currentLine = 0

    private val timer = TickTimer(TimeUnit.SECONDS)

    private enum class Mode {
        IN_ORDER, RANDOM_ORDER
    }

    private enum class SourceMode {
        INTERNAL, EXTERNAL
    }

    private const val CLIENT_NAME = "%CLIENT%"

    private val defaultExcuses = arrayOf(
        "Sorry, im using $CLIENT_NAME",
        "My ping is so bad",
        "I was changing my config :(",
        "Why did my AutoTotem break",
        "I was desynced",
        "Stupid hackers killed me",
        "Wow, so many try hards",
        "Lagggg",
        "I wasn't trying",
        "I'm not using $CLIENT_NAME",
        "Thers to much lag",
        "My dog ate my pc",
        "Sorry, $CLIENT_NAME is really bad",
        "I was lagging",
        "He was cheating!",
        "Your hacking!",
        "Lol imagine actully trying",
        "I didn't move my mouse",
        "I was playing on easy mode(;",
        "My wifi went down",
        "I'm playing vanila",
        "My optifine didn't work",
        "The CPU cheated!",
        "I am using a cracked client",
        "My brother was playing.",
        "Phobos hacked my pc!!",
        "I didn't have enough totems",
        "I died for you <3",
        "I was trying the popbob exploit!!",
        "Sorry, let me relog with ${LambdaMod.NAME}",
        "I was alt tabbing",
        "I was trying out a new mod",
    )

    private val clients = arrayOf(
        "Future Client",
        "Salhack",
        "Pyro",
        "Impact"
    )

    init {
        safeListener<PacketEvent.Receive> {
            if (loadedExcuses.isEmpty() || it.packet !is SPacketUpdateHealth) return@safeListener
            if (it.packet.health <= 0.0f && !isHoldingTotem && timer.tick(3L)) {
                val message = if (modeSetting == Mode.IN_ORDER) getOrdered() else getRandom()
                sendServerMessage(message.replace("%CLIENT%", clients.random()))
            }
        }

        onEnable {
            loadedExcuses.clear()
            currentLine = 0

            when (modeSource) {
                SourceMode.INTERNAL -> loadedExcuses.addAll(defaultExcuses)
                SourceMode.EXTERNAL -> {
                    defaultScope.launch(Dispatchers.IO) {
                        if (!file.exists()) {
                            file.createNewFile()
                            MessageSendHelper.sendErrorMessage("$chatName Excuses file is empty!" +
                                ", please add them in the &7excuses.txt&f under the &7.minecraft/lambda&f directory.")
                            disable()
                            return@launch
                        }

                        try {
                            file.forEachLine { if (it.isNotBlank()) loadedExcuses.add(it.trim()) }
                            MessageSendHelper.sendChatMessage("$chatName Loaded excuse messages!")
                        } catch (e: Exception) {
                            MessageSendHelper.sendErrorMessage("$chatName Failed loading excuses, $e")
                            disable()
                        }
                    }
                }
            }
        }
    }

    private val SafeClientEvent.isHoldingTotem: Boolean
        get() = EnumHand.values().any { player.getHeldItem(it).item == Items.TOTEM_OF_UNDYING }

    private fun getOrdered(): String {
        currentLine %= loadedExcuses.size
        return loadedExcuses[currentLine++]
    }

    private fun getRandom(): String {
        val prevLine = currentLine
        // Avoids sending the same message
        while (loadedExcuses.size != 1 && currentLine == prevLine) {
            currentLine = Random.nextInt(loadedExcuses.size)
        }
        return loadedExcuses[currentLine]
    }
}
