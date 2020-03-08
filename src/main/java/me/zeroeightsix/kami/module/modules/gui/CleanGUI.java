package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/***
 * @author S-B99
 * Updated by S-B99 on 27/12/19
 */
@Module.Info(name = "CleanGUI", category = Module.Category.GUI, showOnArray = Module.ShowOnArray.OFF, description = "Modifies parts of the GUI to be transparent")
public class CleanGUI extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    public Setting<Boolean> inventoryGlobal = register(Settings.b("Inventory", false));
    public static Setting<Boolean> chatGlobal = Settings.b("Chat", true);

    private static CleanGUI INSTANCE = new CleanGUI();

    public CleanGUI() {
        INSTANCE = this;
        register(chatGlobal);
    }

    public static boolean enabled() {
        return INSTANCE.isEnabled();
    }

}
