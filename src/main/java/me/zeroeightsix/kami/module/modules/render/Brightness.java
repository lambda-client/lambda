package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Brightness", description = "Makes everything brighter!", category = Module.Category.RENDER)
public class Brightness extends Module {

    @Setting(name = "Brightness")
    public float brightness = 8;

    @Setting(name = "prev_brightness", hidden = true)
    public float prevBrightness = 1;
    boolean goingDown = false;

    @Override
    protected void onEnable() {
        if (goingDown) {
            mc.gameSettings.gammaSetting = prevBrightness;
            alwaysListening = false;
            goingDown = false;
        }
        prevBrightness = mc.gameSettings.gammaSetting;
    }

    @Override
    public void onUpdate() {
        if (goingDown) {
            float diff = (prevBrightness - mc.gameSettings.gammaSetting);
            mc.gameSettings.gammaSetting += diff * 0.1f;
            if (Math.abs(diff) <= .05f) {
                mc.gameSettings.gammaSetting = prevBrightness;
                setAlwaysListening(false);
                goingDown = false;
            }
        }else
            mc.gameSettings.gammaSetting += (brightness - mc.gameSettings.gammaSetting) * 0.1f;
    }

    @Override
    protected void onDisable() {
        goingDown = true;
        setAlwaysListening(true);
    }

}
