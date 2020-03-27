package me.zeroeightsix.kami.module.modules.hidden;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 * Horribly designed class for uh, running things only once.
 */
@Module.Info(name = "Hidden:RunConfig", category = Module.Category.HIDDEN, showOnArray = Module.ShowOnArray.OFF, description = "Default manager for first runs")
public class RunConfig extends Module {
    private Setting<Boolean> hasRunCapes = register(Settings.b("Capes", false));
    private Setting<Boolean> hasRunDiscordSettings = register(Settings.b("DiscordSettings", false));
    private Setting<Boolean> hasRunFixGui = register(Settings.b("FixGui", false));
    private Setting<Boolean> hasRunTabFriends = register(Settings.b("TabFriends", false));
    private Setting<Boolean> hasRunCustomChat = register(Settings.b("CustomChat", false));

    public void onEnable() {
        ModuleManager.getModuleByName("InfoOverlay").setEnabled(true);
        ModuleManager.getModuleByName("ActiveModules").setEnabled(true);
        ModuleManager.getModuleByName("InventoryViewer").setEnabled(true);
        ModuleManager.getModuleByName("CommandConfig").setEnabled(true);

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
        disable();
    }
}
