package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Brightness", description = "Makes everything brighter!", category = Module.Category.RENDER)
public class Brightness extends Module {

    @Setting(name = "Brightness")
    public float brightness = 16;

    @Setting(name = "prev_brightness", hidden = true)
    public float prevBrightness = 1;

    @Override
    protected void onEnable() {
        prevBrightness = mc.gameSettings.gammaSetting;
    }

    @Override
    public void onUpdate() {
        mc.gameSettings.gammaSetting = brightness;
    }

    @Override
    protected void onDisable() {
        mc.gameSettings.gammaSetting = prevBrightness;
    }

}
