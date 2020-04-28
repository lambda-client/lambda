package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.KamiMod.MODULE_MANAGER
import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.command.syntax.ChunkBuilder
import me.zeroeightsix.kami.module.modules.chat.ChatTimestamp
import me.zeroeightsix.kami.util.MessageSendHelper

/**
 * @author dominikaaaa
 */
class FakeMessageCommand : Command("fakemsg", ChunkBuilder().append("message").append("true|false").build()) {
    override fun call(args: Array<String>) {
        when {
            args[1] == "true" || args[1] == "" -> {
                MessageSendHelper.sendRawChatMessage(getTime() + args[0].replace('&', KamiMod.colour))
            }
            args[1] == "false" -> {
                MessageSendHelper.sendRawChatMessage(args[0].replace('&', KamiMod.colour))
            }
            else -> {
                MessageSendHelper.sendErrorMessage(chatLabel + "The second argument, which allows other chat modules to affect the final message &lmust&r be &7true&f or &7false&f. Use \"double quotes\" around your words to make it 1 argument")
            }
        }
    }

    private fun getTime(): String? {
        return when {
            MODULE_MANAGER.isModuleEnabled(ChatTimestamp::class.java) -> {
                MODULE_MANAGER.getModuleT(ChatTimestamp::class.java).formattedTime
            }
            else -> {
                ""
            }
        }
    }
}