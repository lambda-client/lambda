package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * Created by S-B99 on 11/01/20
 */
@Module.Info(name = "AntiBan", category = Module.Category.PLAYER, description = "Correctly parses oversized packets to prevent disconnect", showOnArray = Module.ShowOnArray.OFF)
public class AntiBan extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    private static AntiBan INSTANCE = new AntiBan();

    public AntiBan() {
        INSTANCE = this;
    }

    public static boolean enabled() {
        return INSTANCE.isEnabled();
    }
}
