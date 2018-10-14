package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.gui.kami.KamiGUI;

/**
 * Created by 086 on 26/12/2017.
 */
public class ReloadCommand extends Command {

    public ReloadCommand() {
        super("reload", new ChunkBuilder().build());
    }

    @Override
    public void call(String[] args) {
        KamiMod.getInstance().guiManager = new KamiGUI();
        KamiMod.getInstance().guiManager.initializeGUI();
        KamiMod.loadConfiguration();
    }
}
