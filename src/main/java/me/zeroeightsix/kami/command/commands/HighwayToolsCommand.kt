package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.command.syntax.ChunkBuilder
import me.zeroeightsix.kami.command.syntax.parsers.EnumParser
import me.zeroeightsix.kami.module.modules.misc.HighwayTools
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.block.Block

/**
 * @author Avanatiker
 * @since 01/09/2020
 */
class HighwayToolsCommand : Command("highwaytools", ChunkBuilder()
    .append("mode", true, EnumParser(arrayOf("material", "filler", "ignore", "reach", "settings")))
    .append("value")
    .build(), "ht") {

    override fun call(args: Array<String?>) {
        when (getSubCommand(args)) {
            SubCommands.SETTINGS -> {
                HighwayTools.printSettings()
            }

            SubCommands.MATERIAL -> {
                val block = Block.getBlockFromName(args[1].toString())

                if (block != null) {
                    HighwayTools.material = block
                    MessageSendHelper.sendChatMessage("Set your building material to &7${block.localizedName}&r.")
                } else {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is not a valid block.")
                }
            }

            SubCommands.FILLER -> {
                val block = Block.getBlockFromName(args[1].toString())

                if (block != null) {
                    HighwayTools.fillerMat = block
                    MessageSendHelper.sendChatMessage("Set your filling material to &7${block.localizedName}&r.")
                } else {
                    MessageSendHelper.sendChatMessage("&7${args[1]}&r is not a valid block.")
                }
            }

            SubCommands.IGNORE_ADD -> {
                val block = Block.getBlockFromName(args[2].toString())

                if (block != null) {
                    val added = HighwayTools.ignoreBlocks.add(block)
                    if (added) {
                        HighwayTools.printSettings()
                        MessageSendHelper.sendChatMessage("Added &7${block.localizedName}&r to ignore list.")
                    } else {

                        MessageSendHelper.sendChatMessage("&7${block.localizedName}&r is already ignored.")
                    }
                } else {
                    MessageSendHelper.sendChatMessage("&7${args[2]}&r is not a valid block.")
                }
            }

            SubCommands.IGNORE_DEL -> {
                val block = Block.getBlockFromName(args[2].toString())

                if (block != null) {
                    val removed = HighwayTools.ignoreBlocks.remove(block)
                    if (removed) {
                        HighwayTools.printSettings()
                        MessageSendHelper.sendChatMessage("Removed &7${block.localizedName}&r from ignore list.")
                    } else {
                        MessageSendHelper.sendChatMessage("&7${block.localizedName}&r is not yet ignored.")
                    }
                } else {
                    MessageSendHelper.sendChatMessage("&7${args[2]}&r is not a valid block.")
                }
            }

            else -> {
                val commands = args.joinToString(separator = " ")
                MessageSendHelper.sendChatMessage("Invalid command &7${commandPrefix.value}${label} $commands&f!")
            }
        }
    }

    private fun getSubCommand(args: Array<String?>): SubCommands? {
        return when {
            args[0].isNullOrBlank() -> SubCommands.SETTINGS

            args[0].equals("settings", ignoreCase = true) -> SubCommands.SETTINGS

            args[1].isNullOrBlank() -> null

            args[0].equals("material", ignoreCase = true) -> SubCommands.MATERIAL

            args[0].equals("filler", ignoreCase = true) -> SubCommands.FILLER

            args[0].equals("ignore", ignoreCase = true) -> {
                when {
                    args[1].equals("add", ignoreCase = true) -> SubCommands.IGNORE_ADD

                    args[1].equals("del", ignoreCase = true) -> SubCommands.IGNORE_DEL

                    else -> null
                }
            }

            else -> null
        }
    }

    private enum class SubCommands {
        MATERIAL, FILLER, IGNORE_ADD, IGNORE_DEL, SETTINGS
    }

    init {
        setDescription("Customize HighwayTools settings.")
    }
}