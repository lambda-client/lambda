package me.zeroeightsix.kami.module.modules.sdashb.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 * Created by S-B99 on 04/12/19
 */

@Module.Info(name = "InfoOverlay", category = Module.Category.GUI, description = "Configures game information overlay")
public class InfoOverlay extends Module {
    public Setting<Boolean> globalInfoNam = register(Settings.b("Username", true));
    public Setting<Boolean> globalInfoTps = register(Settings.b("TPS", true));
    public Setting<Boolean> globalInfoFps = register(Settings.b("FPS", true));
    public Setting<Boolean> globalInfoPin = register(Settings.b("Ping", true));
    public Setting<Boolean> globalInfoMem = register(Settings.b("Memory", true));
}
