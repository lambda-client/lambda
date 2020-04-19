package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;

import static me.zeroeightsix.kami.util.MessageSendHelper.sendChatMessage;

/**
 * Created by Dewy on the 17th of April, 2020
 */
public class BaritoneCommand extends Command {

    public BaritoneCommand() {
        super("baritone", null);
        setDescription("Configure baritone using it's own command system. Try typing '#help'.");
    }

    @Override
    public void call(String[] args) {
        sendChatMessage("KAMI Blue has Baritone integration. To configure Baritone, use Baritone's own command system. Try #help for a command list.");
    }
}
