package me.zeroeightsix.kami.module.modules.hidden;

import me.zeroeightsix.kami.util.LogUtil;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendRawChatMessage;

import net.minecraft.client.Minecraft;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;

import java.io.FileWriter;
import java.io.IOException;

@Module.Info(
        name = "CoordsLog",
        description = "Automatically writes the coordinates of the player to a file with a user defined delay between logs.",
        category = Module.Category.HIDDEN,
        showOnArray = Module.ShowOnArray.OFF
)
public class CoordsLog extends Module {
    private Setting<Double> delay = register(Settings.doubleBuilder("Time between logs").withMinimum(1.0).withValue(15.0).withMaximum(60.0).build());
    private Setting<Boolean> checkDuplicates = register(Settings.b("Don't log same coord 2 times in a row", true));
    private Setting<Boolean> useChunkCoord = register(Settings.b("Use chunk coordinate", true));

    private int previousCoord;

    private static boolean playerIsDead = false;

    @Override
    public void onUpdate() {
        if (mc.player == null)
            return;
        timeout();
        if (!playerIsDead && 0 >= mc.player.getHealth()) {
            logCoordinates();
            playerIsDead = true;
            return;
        }
    }

    private static long startTime = 0;

    private void timeout() {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
        if (startTime + (delay.getValue() * 1000) <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            int[] cCArray = LogUtil.getCurrentCoord(useChunkCoord.getValue());
            int currentCoord = (int) cCArray[0]*3 + (int) cCArray[1]*32 + (int) cCArray[2]/2;
            if (checkDuplicates.getValue() == true) {
                if (currentCoord != previousCoord) {
                    logCoordinates();
                    previousCoord = currentCoord;
                    return;
                }
            } else {
                logCoordinates();
                previousCoord = currentCoord;
                return;
            }
        }
    }

    private void logCoordinates() {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        final String time = sdf.format(new Date());
        LogUtil.writePlayerCoords(time, useChunkCoord.getValue());
    }

    public void onDisable() {
        startTime = 0;
    }
}
