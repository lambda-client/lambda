package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.SyntaxChunk;

import java.io.IOException;

/**
 * Created by 086 on 4/10/2018.
 */
public class SaveCommand extends Command {
    public SaveCommand() {
        super("save", SyntaxChunk.EMPTY);
        setDescription("Saves the current settings to disk");
    }

    @Override
    public void call(String[] args) {
        try {
            KamiMod.saveConfigurationUnsafe();
        } catch (IOException e) {
            e.printStackTrace();
            Command.sendChatMessage("Failed to save! " + e.getMessage());
        }
    }
}
