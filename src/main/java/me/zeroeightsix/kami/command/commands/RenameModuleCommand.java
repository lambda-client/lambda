package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import me.zeroeightsix.kami.command.syntax.parsers.ModuleParser;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;

public class RenameModuleCommand extends Command {

    public RenameModuleCommand() {
        super("renamemodule", new ChunkBuilder().append("module", true, new ModuleParser()).append("name").build());
    }

    @Override
    public void call(String[] args) {
        if (args.length == 0) {
            sendChatMessage("Please specify a module!");
            return;
        } else if (args.length == 1) {
            sendChatMessage("Please specify a name!");
            return;
        }

        Module module = ModuleManager.getModuleByName(args[0]);
        if (module == null) {
            sendChatMessage("Unknown module '" + args[0] + "'!");
            return;
        }

        String name = args[1];

        if (!(name.matches("[a-zA-Z]+"))) {
            sendChatMessage("Name must be alphabetic!");
            return;
        }

        sendChatMessage("&b" + module.getName() + "&r renamed to &b" + name);
        module.setName(name);
    }

}
