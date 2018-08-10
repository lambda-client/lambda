package me.zeroeightsix.kami.event.events;

import me.zeroeightsix.kami.event.KamiEvent;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

/**
 * @author 086
 */
public class RenderTileEntityEvent extends KamiEvent {

    TileEntity entity;

    public RenderTileEntityEvent(TileEntity entity) {
        this.entity = entity;
    }

    public TileEntity getEntity() {
        return entity;
    }

    public void setEntity(TileEntity entity) {
        this.entity = entity;
    }
}
