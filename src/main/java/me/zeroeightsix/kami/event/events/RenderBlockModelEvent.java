package me.zeroeightsix.kami.event.events;

import me.zeroeightsix.kami.event.KamiEvent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author 086
 */
public class RenderBlockModelEvent extends KamiEvent {

    private IBlockAccess world;
    private IBakedModel model;
    private IBlockState state;
    private BlockPos pos;
    private BufferBuilder buffer;
    private boolean checkSides;
    private long rand;

    public RenderBlockModelEvent(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand) {
        this.world = world;
        this.model = model;
        this.state = state;
        this.pos = pos;
        this.buffer = buffer;
        this.checkSides = checkSides;
        this.rand = rand;
    }

    public IBlockAccess getWorld() {
        return world;
    }

    public void setWorld(IBlockAccess world) {
        this.world = world;
    }

    public IBakedModel getModel() {
        return model;
    }

    public void setModel(IBakedModel model) {
        this.model = model;
    }

    public IBlockState getState() {
        return state;
    }

    public void setState(IBlockState state) {
        this.state = state;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public BufferBuilder getBuffer() {
        return buffer;
    }

    public void setBuffer(BufferBuilder buffer) {
        this.buffer = buffer;
    }

    public boolean isCheckSides() {
        return checkSides;
    }

    public void setCheckSides(boolean checkSides) {
        this.checkSides = checkSides;
    }

    public long getRand() {
        return rand;
    }

    public void setRand(long rand) {
        this.rand = rand;
    }
}
