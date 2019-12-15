package me.zeroeightsix.kami.module.modules.bewwawho.misc;

import me.zeroeightsix.kami.DiscordPresence;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 */
@Module.Info(name = "DiscordRPC", category = Module.Category.MISC, description = "Discord Rich Presence")
public class BlueDiscordRPC extends Module {

    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    public Setting<Boolean> serverGlobal = register(Settings.b("Server Status", true));
    public Setting<Boolean> ipGlobal = register(Settings.b("Server IP", true));

    public void onEnable() {
        DiscordPresence.start();
    }
}
