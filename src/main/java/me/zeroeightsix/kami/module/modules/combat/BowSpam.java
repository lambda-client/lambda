package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.item.ItemBow;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerDigging.Action;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.math.BlockPos;

/**
 * Code like this does not have an author, as it is literally one function. It's nothing unique.
 * This is just one thing. Anybody can write it the exact same way on accident.
 * There is nothing to credit here.
 * Updated by S-B99 on 28/01/20
 */
@Module.Info(name = "BowSpam", description = "Makes you spam arrows", category = Module.Category.COMBAT)
public class BowSpam extends Module {

    public void onUpdate() {
        if (mc.player.getHeldItemMainhand().getItem() instanceof ItemBow && mc.player.isHandActive() && mc.player.getItemInUseMaxCount() >= 3) {
            mc.player.connection.sendPacket(new CPacketPlayerDigging(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, mc.player.getHorizontalFacing()));
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItem(mc.player.getActiveHand()));
            mc.player.stopActiveHand();
        }
    }
}
