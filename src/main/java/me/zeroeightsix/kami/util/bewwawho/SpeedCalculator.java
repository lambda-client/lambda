package me.zeroeightsix.kami.util.bewwawho;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.bewwawho.gui.InfoOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

import java.text.DecimalFormat;

/**
 * @author S-B99
 * Created by S-B99 on 18/01/20
 *
 *  * Credit to Seppuku for the following calculation I made more efficient and got inspiration from
 *  * final String bps = "BPS: " + df.format((MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ) / tickRate));
 */
public class SpeedCalculator extends Module {
    private static DecimalFormat formatter = new DecimalFormat("#.#");
    private static InfoOverlay info = (InfoOverlay) ModuleManager.getModuleByName("InfoOverlay");

    public static String speed() {
        float currentTps = mc.timer.tickLength / 1000.0f;
        if (info.useUnitKmH()) {
            return formatter.format(((MathHelper.sqrt(Math.pow(coordsDiff("x"), 2) + Math.pow(coordsDiff("y"), 2)) / currentTps)) * 3.6); // convert mps to kmh
        }
        else {
            return formatter.format((MathHelper.sqrt(Math.pow(coordsDiff("x"), 2) + Math.pow(coordsDiff("y"), 2)) / currentTps));
        }
    }

    private static double coordsDiff(String s) {
        switch(s) {
            case "x": return mc.player.posX - mc.player.prevPosX;
            case "z": return mc.player.posZ - mc.player.prevPosZ;
            default: return 0.0;
        }
    }
}
