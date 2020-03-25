package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.hidden.FixGui;

/**
 * @author S-B99
 */
public class FixGuiCommand extends Command {
    public FixGuiCommand() {
        super("fixgui", new ChunkBuilder().build());
        setDescription("Allows you to disable the automatic gui positioning");
    }

    @Override
    public void call(String[] args) {
        FixGui fixGui = (FixGui) ModuleManager.getModuleByName("Hidden:FixGui");
        if (fixGui.isEnabled() && fixGui.shouldAutoEnable.getValue()) {
            fixGui.shouldAutoEnable.setValue(false);
            fixGui.disable();
            Command.sendChatMessage("[" + getLabel() + "] Disabled");
        }
        else {
            fixGui.shouldAutoEnable.setValue(true);
            fixGui.enable();
            Command.sendChatMessage("[" + getLabel() + "] Enabled");
        }
    }
}
