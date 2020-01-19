package me.zeroeightsix.kami.util.bewwawho;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.LogWrapper;

/**
 * @author S-B99
 * Created by S-B99 on 04/12/19
 */
public class PingCalculator extends Module {

    public static int ping() {
        if (mc.getConnection() == null) { // tested, this is not null in mp
            return 1;
        } else if (mc.player == null) { // this actually takes about 30 seconds to load in Minecraft
            return -1;
        } else {
            try {
                return mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getResponseTime();
            } catch (NullPointerException npe) {
                LogWrapper.info("Caught NPE PingCalculator.java");
            }
            return -1;
        }
    }
}

