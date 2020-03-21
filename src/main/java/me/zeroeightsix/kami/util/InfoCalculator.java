package me.zeroeightsix.kami.util;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.text.DecimalFormat;

/**
 * @author S-B99
 * Created by S-B99 on 18/01/20
 * Updated by S-B99 on 06/02/20
 *
 * Speed:
 * @author S-B99
 * Created by S-B99 on 18/01/20
 * Credit to Seppuku for the following calculation I made more efficient and got inspiration from
 * final String bps = "BPS: " + df.format((MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ) / tickRate));
 *
 * Durability:
 * @author TBM
 * Created by TBM on 8/12/19
 *
 * TPS:
 * @author 086
 */
public class InfoCalculator extends Module {

    /* Ping */
    public static int ping() {
        if (mc.getConnection() == null) { // tested, this is not null in mp
            return 1;
        } else if (mc.player == null) { // this actually takes about 30 seconds to load in Minecraft
            return -1;
        } else {
            try {
                return mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getResponseTime();
            } catch (NullPointerException ignored) {
            }
            return -1;
        }
    }
    /* End of Ping */

    /* Speed */
    private static DecimalFormat formatter = new DecimalFormat("#.#");
    public static String speed(boolean useUnitKmH) {
        float currentTps = mc.timer.tickLength / 1000.0f;
        double multiply = 1.0;
        if (useUnitKmH) multiply = 3.6; // convert mps to kmh
        return formatter.format(((MathHelper.sqrt(Math.pow(coordsDiff("x"), 2) + Math.pow(coordsDiff("z"), 2)) / currentTps)) * multiply);
    }

    private static double coordsDiff(String s) {
        switch (s) {
            case "x": return mc.player.posX - mc.player.prevPosX;
            case "z": return mc.player.posZ - mc.player.prevPosZ;
            default: return 0.0;
        }
    }
    /* End of Speed*/

    /* Durability*/
    public static int dura() {
        ItemStack itemStack = Wrapper.getMinecraft().player.getHeldItemMainhand();
        return itemStack.getMaxDamage() - itemStack.getItemDamage();
    }
    /* End of Durability */

    /* Memory */
    public static String memory() {
        return "" + (Runtime.getRuntime().freeMemory() / 1000000);
    }
    /* End of Memory*/

    /* Ticks Per Second */
    public static String tps() {
        return "" + Math.round(LagCompensator.INSTANCE.getTickRate());
    }
    /* End of ticks Per Second */

    /* Round */
    public static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
    /* End of round */

    /* Is Even */
    public static boolean isNumberEven(int i) { return (i & 1) == 0; }
    /* End of Is Even */
}
