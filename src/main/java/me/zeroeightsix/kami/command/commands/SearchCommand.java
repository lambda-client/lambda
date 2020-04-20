package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.module.modules.render.Search;
import net.minecraft.block.Block;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;
import static me.zeroeightsix.kami.util.MessageSendHelper.*;

/**
 * Created by 20kdc on 17/02/2020.
 * Updated by S-B99 on 17/02/20
 * Modified for use with search module by wnuke on 20/04/2020
 */
public class SearchCommand extends Command {
    public SearchCommand() {
        super("search", new ChunkBuilder().append("help").append("+block|-block|=block").append("list|defaults|clear").build());
        setDescription("Allows you to add or remove blocks from the &fSearch &7module");
    }

    private static final String ESP_BANNED_BLOCKS = "minecraft:air, minecraft:netherrack, minecraft:dirt, minecraft:water";

    @Override
    public void call(String[] args) {
        Search search = MODULE_MANAGER.getModuleT(Search.class);
        if (search == null) {
            sendErrorMessage("&cThe module is not available for some reason. Make sure the name you're calling is correct and that you have the module installed!!");
            return;
        }
        if (!search.isEnabled()) {
            sendWarningMessage("&6Warning: The " + search.getName() + " module is not enabled!");
            sendWarningMessage("These commands will still have effect, but will not visibly do anything.");
        }
        for (String s : args) {
            if (s == null)
                continue;
            if (s.equalsIgnoreCase("help")) {
                sendChatMessage("The " + search.getName() + " module has a list of blocks");
                sendChatMessage("Normally, the " + search.getName() + " module highlights these blocks");
                sendChatMessage("This command is a convenient way to quickly edit the list");
                sendChatMessage("Available options: \n" +
                        "+block: Adds a block to the list\n" +
                        "-block: Removes a block from the list\n" +
                        "=block: Changes the list to only that block\n" +
                        "list: Prints the list of selected blocks\n" +
                        "defaults: Resets the list to the default list\n" +
                        "clear: Removes all blocks from the " + search.getName() + " block list");
            } else if (s.equalsIgnoreCase("clear")) {
                search.extClear();
                sendWarningMessage("Cleared the " + search.getName() + " block list");
            } else if (s.equalsIgnoreCase("defaults")) {
                search.extDefaults();
                sendChatMessage("Reset the " + search.getName() + " block list to default");
            } else if (s.equalsIgnoreCase("list")) {
                sendChatMessage("\n" + search.extGet());
            } else if (s.startsWith("=")) {
                String sT = s.replace("=" ,"");
                search.extSet(sT);
                sendChatMessage("Set the " + search.getName() + " block list to " + sT);
            } else if (s.startsWith("+") || s.startsWith("-")) {
                String name = s.substring(1);
                Block b = Block.getBlockFromName(name);
                if (b == null) {
                    sendChatMessage("&cInvalid block name <" + name + ">");
                } else {
                    if (s.startsWith("+")) {
                        if (!ESP_BANNED_BLOCKS.contains(name)) {
                            sendChatMessage("Added <" + name + "> to the " + search.getName() + " block list");
                            search.extAdd(name);
                        } else {
                            sendChatMessage("You can't add <" + name + "> to the " + search.getName() + " block list");
                        }
                    } else {
                        sendChatMessage("Removed <" + name + "> from the " + search.getName() + " block list");
                        search.extRemove(name);
                    }
                }
            } else {
                sendChatMessage("&cInvalid subcommand <" + s + ">");
            }
        }
    }
}
