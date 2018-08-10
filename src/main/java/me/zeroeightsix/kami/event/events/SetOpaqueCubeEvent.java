package me.zeroeightsix.kami.event.events;

import me.zeroeightsix.kami.event.KamiEvent;
import net.minecraft.util.math.BlockPos;

/**
 * @author 086
 */
public class SetOpaqueCubeEvent extends KamiEvent  {

    BlockPos pos;

    public SetOpaqueCubeEvent(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

}
