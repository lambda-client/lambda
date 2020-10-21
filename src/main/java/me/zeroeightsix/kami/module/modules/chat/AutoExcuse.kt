package me.zeroeightsix.kami.module.modules.chat

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.network.play.server.SPacketUpdateHealth
import java.io.File

@Module.Info(
        name = "AutoExcuse",
        description = "Makes an excuse for you when you die",
        category = Module.Category.CHAT
)
object AutoExcuse : Module() {
    private val mode = register(Settings.e<Mode>("Mode", Mode.INTERNAL))

    private enum class Mode {
        INTERNAL, EXTERNAL
    }

    private const val CLIENT_NAME = "%CLIENT%"
    private val defaultExcuses= arrayOf(
            "Sorry, im using $CLIENT_NAME client",
            "My ping is so bad",
            "I was changing my config :(",
            "Why did my AutoTotem break",
            "I was desynced",
            "Stupid hackers killed me",
            "Wow, so many try hards",
            "Lagggg",
            "I wasn't trying",
            "I'm not using $CLIENT_NAME client"
    )

    private val file = File("excuses.txt")
    private var loadedExcuses = defaultExcuses

    private val clients = arrayOf(
            "Future",
            "Salhack",
            "Pyro",
            "Impact"
    )

    private val timer = TimerUtils.TickTimer(TimerUtils.TimeUnit.SECONDS)

    init {
        listener<PacketEvent.Receive> {
            if (mc.player == null || it.packet !is SPacketUpdateHealth) return@listener
            if (it.packet.health <= 0f && timer.tick(3L)) {
                MessageSendHelper.sendServerMessage(getExcuse())
            }
        }
    }

    override fun onEnable() {
        loadedExcuses = if (mode.value == Mode.EXTERNAL) {
            if (file.exists()) {
                val cacheList = ArrayList<String>()
                try {
                    file.forEachLine { if (it.isNotEmpty()) cacheList.add(it.removeWhiteSpace()) }
                } catch (e: Exception) {
                    KamiMod.log.error("Failed loading excuses", e)
                }
                cacheList.toTypedArray()
            } else {
                file.createNewFile()
                MessageSendHelper.sendErrorMessage("$chatName Excuses file is empty!" +
                        ", please add them in the &7excuses.txt&f under the &7.minecraft&f directory.")
                defaultExcuses
            }
        } else {
            defaultExcuses
        }
    }

    private fun getExcuse() = loadedExcuses.random().replace(CLIENT_NAME, clients.random())

    private fun String.removeWhiteSpace() = this.replace("^( )+".toRegex(), "").replace("( )+$".toRegex(), "")
}
