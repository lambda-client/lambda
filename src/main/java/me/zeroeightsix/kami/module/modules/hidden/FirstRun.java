package me.zeroeightsix.kami.module.modules.hidden;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author S-B99
 * Horribly designed class for uh, running things only once.
 */
@Module.Info(name = "Hidden:FirstRun", category = Module.Category.HIDDEN, showOnArray = Module.ShowOnArray.OFF, description = "Default manager for first runs")
public class FirstRun extends Module {
    private Setting<Boolean> hasRunCapes = register(Settings.b("Capes", false));
    private Setting<Boolean> hasRunDiscordSettings = register(Settings.b("DiscordSettings", false));
    private Setting<Boolean> hasRunFixGui = register(Settings.b("FixGui", false));
    private Setting<Boolean> hasRunTabFriends = register(Settings.b("TabFriends", false));
    private Setting<Boolean> hasRunCustomChat = register(Settings.b("CustomChat", false));
    private Setting<Boolean> hasRunPrefixChat = register(Settings.b("CapesPrefixChat", false));

    public void onUpdate() {
        if (!hasRunCapes.getValue()) {
            ModuleManager.getModuleByName("Capes").setEnabled(true);
            hasRunCapes.setValue(true);
        }
        if (!hasRunDiscordSettings.getValue()) {
            ModuleManager.getModuleByName("DiscordSettings").setEnabled(true);
            hasRunDiscordSettings.setValue(true);
        }
        if (!hasRunFixGui.getValue()) {
            ModuleManager.getModuleByName("Hidden:FixGui").setEnabled(true);
            hasRunFixGui.setValue(true);
        }
        if (!hasRunTabFriends.getValue()) {
            ModuleManager.getModuleByName("TabFriends").setEnabled(true);
            hasRunTabFriends.setValue(true);
        }
        if (!hasRunCustomChat.getValue()) {
            ModuleManager.getModuleByName("CustomChat").setEnabled(true);
            hasRunCustomChat.setValue(true);
        }
        if (!hasRunPrefixChat.getValue()) {
            ModuleManager.getModuleByName("PrefixChat").setEnabled(true);
            hasRunPrefixChat.setValue(true);
        }
    }

    public static void runAliases(Command command) {
        int amount = command.getAliases().size();
        if (amount > 0) {
            Command.sendChatMessage("'" + command.getLabel() + "' has " + grammar1(amount) + "alias" + grammar2(amount));
            List<String> aliases = new ArrayList<>();
            for (String aliasesCmd : command.getAliases()) {
                aliases.add(aliasesCmd + ", ");
            }
            Command.sendChatMessage(aliases.toString());
        }
    }

    private static String grammar1(int amount) {
        if (amount == 1) return "an ";
        return amount + " ";
    }

    private static String grammar2(int amount) {
        if (amount == 1) return "!";
        return "es!";
    }
}
