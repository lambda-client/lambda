package me.zeroeightsix.kami.module.modules.render;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

import java.util.ArrayList;

/**
 * Created by 086 on 15/12/2017.
 */
@Module.Info(name = "PortalTracers", category = Module.Category.RENDER)
public class PortalTracers extends Module {

    @Setting(name = "Range") private int range = 5000;

    ArrayList<BlockPos> portals = new ArrayList<>();

    @EventHandler
    private Listener<ChunkEvent.Load> loadListener = new Listener<>(event -> {
        Chunk chunk = event.getChunk();

        // Remove already registered portals from this chunk, allowing removed portals to vanish from tracers and no duplicates to be made
        portals.removeIf(blockPos -> blockPos.getX()/16 == chunk.x && blockPos.getZ()/16 == chunk.z);

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 256; y++) {
                for (int z = 0; z < 16; z++) {
                    IBlockState blockState = chunk.getBlockState(x, y, z);
                    if (blockState.getBlock() instanceof BlockPortal){
                        int px = chunk.x*16 + x;
                        int py = y;
                        int pz = chunk.z*16 + z;

                        portals.add(new BlockPos(px, py, pz));
                        y+=6; // Skip a few y-layers because we're cool (non performance-heavy way of avoiding multiple lines to the same portal, and i'm just lazy)
                    }
                }
            }
        }
    });


    @Override
    public void onWorldRender(RenderEvent event) {
        portals.stream().filter(blockPos -> mc.player.getDistance(blockPos.x,blockPos.y,blockPos.z)<=range)
                .forEach(blockPos -> Tracers.drawLine(blockPos.x-mc.getRenderManager().renderPosX, blockPos.y-mc.getRenderManager().renderPosY, blockPos.z-mc.getRenderManager().renderPosZ, 0,0.6f, 0.3f, 0.8f,1));
    }
}
