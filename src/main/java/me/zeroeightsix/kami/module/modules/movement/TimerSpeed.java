package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.Module.Category;
import me.zeroeightsix.kami.module.Module.Info;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.InfoCalculator;

/**
 * @author TBM
 * Updated by S-B99 on 28/01/20
 */
@Info(name = "TimerSpeed", description = "Automatically change Timer Speed", category = Category.MOVEMENT)
public class TimerSpeed extends Module {
    private float tickDelay = 0.0f;
    private static float curSpeed = 0.0f;
    private Setting<Float> minimumSpeed = register(Settings.floatBuilder("Minimum Speed").withMinimum(0.0F).withMaximum(10.0F).withValue(4.0F));
    private Setting<Float> maxSpeed = register(Settings.floatBuilder("Max Speed").withMinimum(0.0F).withMaximum(10.0F).withValue(7.0F));
    private Setting<Float> attemptSpeed = register(Settings.floatBuilder("Attempt Speed").withMinimum(1.0F).withMaximum(10.0F).withValue(4.2F));
    private Setting<Float> fastSpeed = register(Settings.floatBuilder("Fast Speed").withMinimum(1.0F).withMaximum(10.0F).withValue(5.0F));

    public static String returnGui() {
        return "" + InfoCalculator.round(curSpeed, 2);
    }

    public void onUpdate() {
        if (tickDelay == minimumSpeed.getValue()) {
            curSpeed = fastSpeed.getValue();
            mc.timer.tickLength = 50.0F / fastSpeed.getValue();
        }

        if (tickDelay >= maxSpeed.getValue()) {
            tickDelay = 0;
            curSpeed = attemptSpeed.getValue();
            mc.timer.tickLength = 50.0F / attemptSpeed.getValue();
        }
        ++tickDelay;
    }
    public void onDisable() {
        mc.timer.tickLength = 50.0F;
    }

}
