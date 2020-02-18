package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.misc.ChatSuffix;

/**
 * @author S-B99
 * Created by S-B99 on 17/02/20
 */
public class CustomChatCommand extends Command {
    public CustomChatCommand() {
        super("customchat", new ChunkBuilder().append("ending").build(), "chat");
        setDescription("Allows you to customize ChatSuffix's custom setting");
    }

    @Override
    public void call(String[] args) {
        ChatSuffix cC = (ChatSuffix) ModuleManager.getModuleByName("ChatSuffix");
        if (cC == null) {
            Command.sendErrorMessage("&cThe ChatSuffix module is not available for some reason. Make sure the name you're calling is correct and that you have the module installed!!");
            return;
        }
        if (!cC.isEnabled() || !cC.textMode.getValue().equals(ChatSuffix.TextMode.CUSTOM)) {
            Command.sendWarningMessage("&6Warning: The ChatSuffix module is not enabled, or you don't have custom mode enabled!");
            Command.sendWarningMessage("The command will still work, but will not visibly do anything.");
        }
        for (String s : args) {
            if (s == null)
                continue;
            cC.customText.setValue(s);
            Command.sendChatMessage("Set the Custom Text Mode to <" + s + ">");
        }
    }
}
