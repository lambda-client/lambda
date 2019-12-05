package me.zeroeightsix.kami.module.modules.sdashb.util;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.LogWrapper;

import static net.minecraft.launchwrapper.LogWrapper.log;

/**
 * @author S-B99
 * Created by S-B99 on 04/12/19
 */

public class CalcPing {

    public static int globalInfoPingValue() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) { // tested, this is not null in mp
            return 1;
        }
        else if (mc.player == null) { // this actually takes about 30 seconds to load in Minecraft
            return -1;
        }
        else {
            try {
                return mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getResponseTime();
            } catch (NullPointerException npe) {
                LogWrapper.info("Caught NPE l27 CalcPing.java");
            }
            return -1;
        }

    }
}

