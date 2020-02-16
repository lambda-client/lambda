package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.module.Module;

@Module.Info(name = "Strength Detect", category = Module.Category.COMBAT, description = "Displays active strength effect of players in the text radar", showOnArray = Module.ShowOnArray.OFF)
public class StrengthDetect extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
}
