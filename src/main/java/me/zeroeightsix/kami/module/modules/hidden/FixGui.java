package me.zeroeightsix.kami.module.modules.hidden;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

import static me.zeroeightsix.kami.util.GuiFrameUtil.fixFrames;

/**
 * @author S-B99
 * @see me.zeroeightsix.kami.command.commands.FixGuiCommand
 */
@Module.Info(name = "Hidden:FixGui", category = Module.Category.HIDDEN, showOnArray = Module.ShowOnArray.OFF, description = "Moves GUI elements back on screen")
public class FixGui extends Module {
    public Setting<Boolean> shouldAutoEnable = register(Settings.b("Enable", true));
    public void onUpdate() {
        fixFrames(mc);
    }
}
