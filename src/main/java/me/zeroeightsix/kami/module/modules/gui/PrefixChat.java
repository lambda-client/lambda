package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

@Module.Info(name = "PrefixChat", category = Module.Category.GUI, description = "Opens chat with prefix inside when prefix is pressed.", showOnArray = Module.ShowOnArray.OFF)
public class PrefixChat extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));

    public void onDisable() { Command.sendAutoDisableMessage(getName(), startupGlobal.getValue()); }
}
