package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.module.modules.combat.AutoEZ;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;

public class AutoEZCommand extends Command {
    public AutoEZCommand() {
        super("autoez", new ChunkBuilder().append("message").build());
        setDescription("Allows you to customize AutoEZ's custom setting");
    }

    @Override
    public void call(String[] args) {
        AutoEZ az = MODULE_MANAGER.getModuleT(AutoEZ.class);
        if (az == null) {
            Command.sendErrorMessage("&cThe AutoEZ module is not available for some reason. Make sure the name you're calling is correct and that you have the module installed!!");
            return;
        }
        if (!az.isEnabled()) {
            Command.sendWarningMessage("&6Warning: The AutoEZ module is not enabled!");
            Command.sendWarningMessage("The command will still work, but will not visibly do anything.");
        }
        if (!az.mode.getValue().equals(AutoEZ.Mode.CUSTOM)) {
            Command.sendWarningMessage("&6Warning: You don't have custom mode enabled in AutoEZ!");
            Command.sendWarningMessage("The command will still work, but will not visibly do anything.");
        }
        for (String s : args) {
            if (s == null)
                continue;
            az.customText.setValue(s);
            Command.sendChatMessage("Set the Custom Mode to <" + s + ">");
        }
    }
}
