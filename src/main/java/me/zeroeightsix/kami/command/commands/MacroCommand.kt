package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.command.syntax.ChunkBuilder
import me.zeroeightsix.kami.module.Macros
import me.zeroeightsix.kami.util.Macro
import me.zeroeightsix.kami.util.MessageSendHelper.*
import me.zeroeightsix.kami.util.Wrapper

/**
 * @author dominikaaaa
 * Created by dominikaaaa on 04/05/20
 */
class MacroCommand : Command("macro", ChunkBuilder().append("key|list").append("clear|message/command").build(), "m") {
    override fun call(args: Array<out String?>) {
        val rKey = args[0]
        val macro = args[1]
        val key = Wrapper.getKey(rKey)

        if (key == 0 && !rKey.equals("list", true)) {
            sendErrorMessage("Unknown key '&7$rKey&f'! Left alt is &7lmenu&f and left Control is &7lctrl&f. You cannot bind the &7meta&f key.")
            return
        }

        when {
            args[0] == null -> { /* key, error message is caught by the command handler but you don't want to continue the rest */
                return
            }
            args[0] == "list" -> {
                sendChatMessage("You have the following macros: ")
                for ((key1, value) in Macros.macros) {
                    sendChatMessage(Wrapper.getKeyName(key1.toInt()) + ": $value")
                }
                return
            }
            args[1] == null -> { /* message */
                if (Macro.getMacrosForKey(key) == null || Macro.getMacrosForKey(key)?.equals("")!! || Macro.getMacrosForKey(key)?.toTypedArray()?.equals("")!!) {
                    sendChatMessage("'&7$rKey&f' has no macros")
                    return
                }
                sendChatMessage("'&7$rKey&f' has the following macros: ")
                sendStringChatMessage(Macro.getMacrosForKey(key)?.toTypedArray(), false)
                return
            }
            args[1] == "clear" -> {
                Macro.removeMacro(key.toString())
                sendChatMessage("Cleared macros for '&7$rKey&f'")
                return
            }
            args[2] != null -> { /* some random 3rd argument which shouldn't exist */
                sendWarningMessage("$chatLabel Your macro / command must be inside quotes, as 1 argument in the command. Example: &7" + getCommandPrefix() + label + " R \";set AutoSpawner debug toggle\"")
                return
            }
            else -> {
                Macro.addMacroToKey(key.toString(), macro)
                sendChatMessage("Added macro '&7$macro&f' for key '&7$rKey&f'")
            }
        }
    }
}