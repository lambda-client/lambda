package org.kamiblue.client.module.modules.chat

import net.minecraft.world.EnumDifficulty
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.client.util.TickTimer
import org.kamiblue.client.util.TimeUnit
import org.kamiblue.client.util.text.MessageSendHelper
import org.kamiblue.client.util.text.MessageSendHelper.sendServerMessage
import org.kamiblue.client.util.threads.safeListener
import java.text.SimpleDateFormat
import java.util.*

internal object AutoQMain : Module(
    name = "AutoQMain",
    description = "Automatically does '/queue main'",
    category = Category.CHAT,
    showOnArray = false
) {
    private val delay by setting("Delay", 30, 1..120, 5)
    private val twoBeeCheck by setting("2B Check", true)
    private val command by setting("Command", "/queue main")

    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (!timer.tick(delay.toLong()) ||
                world.difficulty != EnumDifficulty.PEACEFUL ||
                player.dimension != 1) return@safeListener

            if (twoBeeCheck) {
                if (@Suppress("UNNECESSARY_SAFE_CALL")
                    player.serverBrand?.contains("2b2t") == true) sendQueueMain()
            } else {
                sendQueueMain()
            }
        }
    }

    private fun sendQueueMain() {
        val formatter = SimpleDateFormat("HH:mm:ss")
        val date = Date(System.currentTimeMillis())

        MessageSendHelper.sendChatMessage("&7Run &b$command&7 at " + formatter.format(date))
        sendServerMessage(command)
    }
}
