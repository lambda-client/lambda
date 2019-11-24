package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.math.BlockPos;

/**
 * Author Seth 4/30/2019 @ 4:27 AM.
 * <p>
 * see <a href=" https://github.com/seppukudevelopment/seppuku/blob/558636579c2c2df5941525d9af1f6e5a4ef658cc/src/main/java/me/rigamortis/seppuku/impl/module/combat/FastBowModule.java">github.com/seppukudevelopment/seppuku</a>
 */
@Module.Info(name = "FastBow", description = "Fast Bow Release", category = Module.Category.COMBAT)
public class FastBow extends Module {

    @Override
    public void onUpdate() {
        if (mc.player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBow &&
                mc.player.isHandActive() && mc.player.getItemInUseMaxCount() >= 3) {
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, mc.player.getHorizontalFacing()));
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItem(mc.player.getActiveHand()));
            mc.player.stopActiveHand();
        }
    }
}
