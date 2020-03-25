package me.zeroeightsix.kami.module.modules.hidden;

import me.zeroeightsix.kami.module.Module;

import static me.zeroeightsix.kami.util.GuiFrameUtil.fixFrames;

@Module.Info(name = "Hidden:FixGui", category = Module.Category.HIDDEN, showOnArray = Module.ShowOnArray.OFF, description = "Moves GUI elements back on screen")
public class FixGui extends Module {
    public void onUpdate() {
        fixFrames(mc);
    }
}
