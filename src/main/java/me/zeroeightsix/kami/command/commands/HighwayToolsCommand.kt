package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.command.syntax.ChunkBuilder
import me.zeroeightsix.kami.command.syntax.parsers.EnumParser
import me.zeroeightsix.kami.module.ModuleManager
import me.zeroeightsix.kami.module.modules.misc.HighwayTools
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.item.Item.getByNameOrId

/**
 * @author Avanatiker
 * @since 01/09/2020
 */
class HighwayToolsCommand : Command("highwaytools", ChunkBuilder()
        .append("mode", true, EnumParser(arrayOf("airspace", "corner", "material", "ignore", "baritone", "logout")))
        .append("value")
        .build(), "ht") {

    override fun call(args: Array<String?>) {
        val subCommand = getSubCommand(args)
        val ht = ModuleManager.getModuleT(HighwayTools::class.java)
        when (subCommand) {
            SubCommands.SHOWSETTINGS -> {
                ht!!.printSettings()
            }

            SubCommands.MATERIAL -> {
                try {
                    val newmat = getByNameOrId(args[1].toString())
                    ht!!.material = args[1].toString()
                    MessageSendHelper.sendChatMessage("&7${newmat}&r is now your building material.")
                } catch (e: Exception) {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is no material.")
                }

            }

            SubCommands.IGNORE -> {
                ht!!.material = args[1].toString()
                MessageSendHelper.sendChatMessage("&7${args[1]}&r is now building material.")
            }

            SubCommands.NULL -> {
                val commands = args.joinToString(separator = " ")
                MessageSendHelper.sendChatMessage("Invalid command &7${commandPrefix.value}${label} $commands&f!")
            }
        }
    }

    private fun getSubCommand(args: Array<String?>): SubCommands {
        return when {
            args[0].isNullOrBlank() -> SubCommands.SHOWSETTINGS

            args[1].isNullOrBlank() -> SubCommands.NULL

            args[0].equals("material", ignoreCase = true) -> SubCommands.MATERIAL

            args[0].equals("ignore", ignoreCase = true) -> SubCommands.IGNORE

            else -> SubCommands.NULL
        }
    }

    private enum class SubCommands {
        MATERIAL, IGNORE, SHOWSETTINGS, NULL
    }

    init {
        setDescription("Customize HighwayTools settings.")
    }
}