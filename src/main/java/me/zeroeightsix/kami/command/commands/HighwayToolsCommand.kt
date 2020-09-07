package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.command.syntax.ChunkBuilder
import me.zeroeightsix.kami.command.syntax.parsers.EnumParser
import me.zeroeightsix.kami.module.ModuleManager
import me.zeroeightsix.kami.module.modules.misc.HighwayTools
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.block.Block

/**
 * @author Avanatiker
 * @since 01/09/2020
 */
class HighwayToolsCommand : Command("highwaytools", ChunkBuilder()
        .append("mode", true, EnumParser(arrayOf("material", "ignore", "settings")))
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
                    val newmat = Block.getBlockFromName(args[1].toString())!!
                    ht!!.material = newmat
                    MessageSendHelper.sendChatMessage("&7${newmat}&r is now your building material.")
                } catch (e: Exception) {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is no block.")
                }
            }

            SubCommands.IGNORE_ADD -> {
                try {
                    val newmat = Block.getBlockFromName(args[2].toString())!!
                    if (newmat !in ht!!.ignoreBlocks) {
                        ht.ignoreBlocks.add(newmat)
                        ht.printSettings()
                        MessageSendHelper.sendChatMessage("&7${newmat}&r is now added to your ignore list.")
                    } else {
                        MessageSendHelper.sendChatMessage("&7${newmat}&r is already ignored.")
                    }
                } catch (e: Exception) {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is no material.")
                }
            }

            SubCommands.IGNORE_DEL -> {
                try {
                    val newmat = Block.getBlockFromName(args[2].toString())!!
                    if (newmat !in ht!!.ignoreBlocks) {
                        ht.ignoreBlocks.remove(newmat)
                        ht.printSettings()
                        MessageSendHelper.sendChatMessage("&7${newmat}&r is now removed from your ignore list.")
                    } else {
                        MessageSendHelper.sendChatMessage("&7${newmat}&r is not yet ignored.")
                    }
                } catch (e: Exception) {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is no material.")
                }
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

            args[0].equals("ignore", ignoreCase = true) && args[2].isNullOrBlank() -> SubCommands.IGNORE_ADD

            args[0].equals("ignore", ignoreCase = true) && args[1].equals("add", ignoreCase = true) -> SubCommands.IGNORE_ADD

            args[0].equals("ignore", ignoreCase = true) && args[1].equals("del", ignoreCase = true) -> SubCommands.IGNORE_DEL

            else -> SubCommands.NULL
        }
    }

    private enum class SubCommands {
        MATERIAL, IGNORE_ADD, IGNORE_DEL, SHOWSETTINGS, NULL
    }

    init {
        setDescription("Customize HighwayTools settings.")
    }
}