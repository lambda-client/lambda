package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.modules.gui.ActiveModules;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;

/**
 * @author S-B99
 * Updated by S-B99 on 04/04/20
 */
public class ActiveModulesCommand extends Command {
    public ActiveModulesCommand() {
        super("activemodules", new ChunkBuilder().append("category").append("r").append("g").append("b").build(), "activemods", "modules");
        setDescription("Allows you to customize ActiveModule's category colours");
    }

    @Override
    public void call(String[] args) {
        if (args[0] == null || args[1] == null || args[2] == null || args[3] == null) {
            Command.sendErrorMessage(getChatLabel() + "Missing arguments! Please fill out the command syntax properly");
            return;
        }

        // TODO: this nulls for some reason
        for (String arg : args) {
            if (Pattern.compile("[^0-9]").matcher(arg).find()) {
                if (!(arg.equals(args[0]))) {
                    Command.sendErrorMessage(getChatLabel() + "Error: argument '" + arg + "' contains a non-numeric character. You can only set numbers as the RGB");
                    return;
                }
            }
        }

        ActiveModules am = MODULE_MANAGER.getModuleT(ActiveModules.class);
        switch (args[0].toLowerCase()) {
            case "chat":
                am.chat.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "combat":
                am.combat.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "experimental":
                am.experimental.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "client":
                am.client.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "render":
                am.render.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "player":
                am.player.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "movement":
                am.movement.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "misc":
                am.misc.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            case "utils":
                am.utils.setValue(args[1] + "," + args[2] + "," + args[3]);
                return;
            default:
                Command.sendErrorMessage("Category '" + args[0] + "' not found! Valid categories: \n" + MODULE_MANAGER.getModules().stream().filter(Module::isProduction).collect(Collectors.toList()));
                return;
        }

    }
}
