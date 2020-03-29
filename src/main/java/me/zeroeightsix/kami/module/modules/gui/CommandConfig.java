package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 */
@Module.Info(name = "CommandConfig", category = Module.Category.GUI, description = "Configures options related to commands", showOnArray = Module.ShowOnArray.OFF)
public class CommandConfig extends Module {
    public Setting<Boolean> aliasInfo = register(Settings.b("Alias Info", true));
    public Setting<Boolean> prefixChat = register(Settings.b("PrefixChat", true));
    public void onDisable() { Command.sendDisableMessage(getName()); }
}
