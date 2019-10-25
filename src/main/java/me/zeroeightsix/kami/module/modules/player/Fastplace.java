package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author 086
 * Updated by S-B99 on 25/10/19
 */
@Module.Info(name = "Fastplace", category = Module.Category.PLAYER, description = "Nullifies block place delay")
public class Fastplace extends Module {

	private Setting<Integer> delay = register(Settings.i("Delay", 0));

    @Override
    public void onUpdate() {
        mc.rightClickDelayTimer = delay.getValue();
    }
}
