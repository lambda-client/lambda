package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author Sazo
 * Updated by S-B99 on 28/01/20
 */
@Module.Info(name = "Timer", category = Module.Category.PLAYER, description = "Changes your client tick speed")
public class Timer extends Module {
    private Setting<Float> speed = register(Settings.floatBuilder("Speed").withMinimum(0f).withMaximum(10f).withValue(4.2f));

    public void onDisable() {
        mc.timer.tickLength = 50.0f;
    }

    public void onUpdate() {
        mc.timer.tickLength = 50.0f / speed.getValue();
    }
}
