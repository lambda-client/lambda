package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;

/**
 * Created by 086 on 10/10/2018.
 */
public class PrefixCommand extends Command {

    public PrefixCommand() {
        super("prefix", new ChunkBuilder().append("character").build());
        setDescription("Changes the prefix to your new key");
    }

    @Override
    public void call(String[] args) {
        if (args.length <= 0) {
            Command.sendChatMessage("Please specify a new prefix!");
            return;
        }

        if (args[0] != null) {
            Command.commandPrefix.setValue(args[0]);
            Command.sendChatMessage("Prefix set to &b" + Command.commandPrefix.getValue());
        }
        else if (args[0].equals("\\")) {
            Command.sendChatMessage("Error: \"\\\" is not a supported prefix");
        }
        else {
            Command.sendChatMessage("Please specify a new prefix!");
        }
    }

}
