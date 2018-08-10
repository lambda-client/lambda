package me.zeroeightsix.kami.event.events;

import net.minecraft.block.Block;

/**
 * @author 086
 */
public class ShouldSideBeRenderedEvent {

    Block block;
    boolean doRender;

    public ShouldSideBeRenderedEvent(Block block, boolean doRender) {
        this.block = block;
        this.doRender = doRender;
    }

    public boolean isDoRender() {
        return doRender;
    }

    public void setDoRender(boolean doRender) {
        this.doRender = doRender;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

}
