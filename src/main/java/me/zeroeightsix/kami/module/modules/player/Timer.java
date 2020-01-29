package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * Code like this does not have an author, as it is literally one function. It's nothing unique.
 * See BowSpam for example. It's just one thing. Anybody can write it the exact same way on accident.
 * There is nothing to credit here. 
 * Updated by S-B99 on 28/01/20
 */
@Module.Info(name = "Timer", category = Module.Category.PLAYER, description = "Changes your client tick speed")
public class Timer extends Module {
    private Setting<Float> speed = register(Settings.floatBuilder("Speed").withMinimum(0f).withMaximum(10f).withValue(2.0f));

    public void onDisable() {
        mc.timer.tickLength = 50.0f;
    }

    public void onUpdate() {
        mc.timer.tickLength = 50.0f / speed.getValue();
    }
}
