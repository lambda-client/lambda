package me.zeroeightsix.kami.util.bewwawho;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.zeroeightysix.Wrapper;
import net.minecraft.item.ItemStack;

/**
 * @author S-B99
 * Created by S-B99 on 18/01/20
 */
public class DurabilityCalculator extends Module {
    public static int dura() {
        ItemStack itemStack = Wrapper.getMinecraft().player.getHeldItemMainhand();
        return itemStack.getMaxDamage() - itemStack.getItemDamage();
    }
}
