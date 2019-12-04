package me.zeroeightsix.kami.module.modules.sdashb.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 * Updated by S-B99 on 30/10/19
 */

@Module.Info(name = "Info", category = Module.Category.GUI, description = "Configures game information overlay")
public class InfoOverlay extends Module {
    public Setting<Boolean> globalInfoTps = register(Settings.b("TPS", true));
    public Setting<Boolean> globalInfoFps = register(Settings.b("FPS", true));
    public Setting<Boolean> globalInfoMem = register(Settings.b("Memory", true));
//    private Setting<Boolean> debug = register(Settings.b("Debug", true));

}
