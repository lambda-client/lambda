package me.zeroeightsix.kami.module.modules.sdashb.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

/***
 * @author Waizy
 */
@Module.Info(name = "HoleESP", category = Module.Category.RENDER)
public class HoleESP
        extends Module {
    private ArrayList<BlockPos> holes = new ArrayList();

    BlockPos pos;

    public void onUpdate() {
        this.holes = new ArrayList();

        Iterable<BlockPos> blocks = BlockPos.getAllInBox(mc.player
                .getPosition().add(-15, -15, -15), mc.player
                .getPosition().add(15, 15, 15));


        for (BlockPos pos : blocks) {

            if (!mc.world.getBlockState(pos).getMaterial().blocksMovement() && !mc.world.getBlockState(pos.add(0, 1, 0)).getMaterial().blocksMovement()) {


                boolean solidNeighbours = (
                        mc.world.getBlockState(pos.add(0, -1, 0)).getBlock() == Blocks.BEDROCK | mc.world.getBlockState(pos.add(0, -1, 0)).getBlock() == Blocks.OBSIDIAN
                                && mc.world.getBlockState(pos.add(1, 0, 0)).getBlock() == Blocks.BEDROCK | mc.world.getBlockState(pos.add(1, 0, 0)).getBlock() == Blocks.OBSIDIAN
                                && mc.world.getBlockState(pos.add(0, 0, 1)).getBlock() == Blocks.BEDROCK | mc.world.getBlockState(pos.add(0, 0, 1)).getBlock() == Blocks.OBSIDIAN
                                && mc.world.getBlockState(pos.add(-1, 0, 0)).getBlock() == Blocks.BEDROCK | mc.world.getBlockState(pos.add(-1, 0, 0)).getBlock() == Blocks.OBSIDIAN
                                && mc.world.getBlockState(pos.add(0, 0, -1)).getBlock() == Blocks.BEDROCK | mc.world.getBlockState(pos.add(0, 0, -1)).getBlock() == Blocks.OBSIDIAN
                                && mc.world.getBlockState(pos.add(0, 0, 0)).getMaterial() == Material.AIR
                                && mc.world.getBlockState(pos.add(0, 1, 0)).getMaterial() == Material.AIR
                                && mc.world.getBlockState(pos.add(0, 2, 0)).getMaterial() == Material.AIR);


                if (solidNeighbours) {
                    this.holes.add(pos);
                }
            }
        }
    }


    public void onWorldRender(RenderEvent event) {
        KamiTessellator.prepare(GL11.GL_QUADS);
        this.holes.forEach(blockPos -> KamiTessellator.drawBox((BlockPos) blockPos, 0x33ffffff, GeometryMasks.Quad.ALL));
        KamiTessellator.release();
    }
}
