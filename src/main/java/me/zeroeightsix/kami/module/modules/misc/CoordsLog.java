package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.util.LogUtil;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;

@Module.Info(name = "CoordsLog", description = "Automatically writes the coordinates of the player to a file with a user defined delay between logs.", category = Module.Category.MISC)
public class CoordsLog extends Module {
    private Setting<Double> delay = register(Settings.doubleBuilder("Time between logs").withMinimum(1.0).withValue(15.0).withMaximum(60.0).build());

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        timeout();
    }

    private static long startTime = 0;
    private void timeout() {
        if (startTime == 0) startTime = System.currentTimeMillis();
        if (startTime + (delay.getValue() * 1000) <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            logCoordinates();
            return;
        }
    }
    
    private void logCoordinates() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        String time = sdf.format(new Date());
        LogUtil.writePlayerCoords(time);
    }

    public void onDisable() { startTime = 0; }
}