package me.zeroeightsix.kami.event.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @author fr1kin
 * Created on 2/10/2018 by fr1kin
 * https://github.com/fr1kin/ForgeHax/blob/2011740/src/main/java/com/matt/forgehax/asm/events/WorldCheckLightForEvent.java
 */
@Cancelable
public class WorldCheckLightForEvent extends Event {
    private final EnumSkyBlock enumSkyBlock;
    private final BlockPos pos;

    public WorldCheckLightForEvent(EnumSkyBlock enumSkyBlock, BlockPos pos) {
        this.enumSkyBlock = enumSkyBlock;
        this.pos = pos;
    }

    public EnumSkyBlock getEnumSkyBlock() {
        return enumSkyBlock;
    }

    public BlockPos getPos() {
        return pos;
    }
}
