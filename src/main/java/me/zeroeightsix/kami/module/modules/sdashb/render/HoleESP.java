package me.zeroeightsix.kami.module.modules.sdashb.render;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BossInfo;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;

/***
 * @author Waizy
 * S-B99 added modes
 */
@Module.Info(name = "HoleESP", category = Module.Category.RENDER)
public class HoleESP extends Module {

    private ArrayList<BlockPos> holesObby = new ArrayList();
    private ArrayList<BlockPos> holesBedr = new ArrayList();

    //private Setting<Integer> range = register(Settings.i("Range", 15));
    private Setting<Distance> distance = register(Settings.e("Distance", Distance.CLOSE));
    private Setting<Boolean> renObby = register(Settings.b("Render Obby", true));
    private Setting<Boolean> renBedr = register(Settings.b("Render Bedrock", true));
    private Setting<Integer> a = register(Settings.integerBuilder("Transparency (Obby)").withMinimum(0).withValue(26).withMaximum(255).build());
    private Setting<Integer> r = register(Settings.integerBuilder("Red (Obby)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> g = register(Settings.integerBuilder("Green (Obby)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> b = register(Settings.integerBuilder("Blue (Obby)").withMinimum(0).withValue(255).withMaximum(255).build());
    private Setting<Integer> a2 = register(Settings.integerBuilder("Transparency (Bedrock)").withMinimum(0).withValue(26).withMaximum(255).build());
    private Setting<Integer> r2 = register(Settings.integerBuilder("Red (Bedrock)").withMinimum(0).withValue(208).withMaximum(255).build());
    private Setting<Integer> g2 = register(Settings.integerBuilder("Green (Bedrock)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> b2 = register(Settings.integerBuilder("Blue (Bedrock)").withMinimum(0).withValue(255).withMaximum(255).build());

    private enum Distance {
        CLOSE, MID, FAR
    }

    public void onUpdate() {
        Iterable<BlockPos> blocks = null;
        this.holesObby = new ArrayList();
        this.holesBedr = new ArrayList();

        if (distance.getValue().equals(Distance.CLOSE)) {
            blocks = BlockPos.getAllInBox(mc.player.getPosition().add(-5, -5, -5), mc.player.getPosition().add(5, 5, 5));
        }
        else if (distance.getValue().equals(Distance.FAR)) {
            blocks = BlockPos.getAllInBox(mc.player.getPosition().add(-20, -20, -20), mc.player.getPosition().add(20, 20, 20));
        }
        else if (distance.getValue().equals(Distance.MID)) {
            blocks = BlockPos.getAllInBox(mc.player.getPosition().add(-12, -12, -12), mc.player.getPosition().add(12, 12, 12));
        }

        if (blocks == null) {
            Command.sendErrorMessage("[HoleESP] Caught NPE, contact Bella about this");
            return;
        }

        for (BlockPos pos : blocks) {
            Block pos1 = mc.world.getBlockState(pos.add(0, -1, 0)).getBlock();
            Block pos2 = mc.world.getBlockState(pos.add(1, 0, 0)).getBlock();
            Block pos3 = mc.world.getBlockState(pos.add(0, 0, 1)).getBlock();
            Block pos4 = mc.world.getBlockState(pos.add(-1, 0, 0)).getBlock();
            Block pos5 = mc.world.getBlockState(pos.add(0, 0, -1)).getBlock();
            Material pos6 = mc.world.getBlockState(pos.add(0, 0, 0)).getMaterial();
            Material pos7 = mc.world.getBlockState(pos.add(0, 1, 0)).getMaterial();
            Material pos8 = mc.world.getBlockState(pos.add(0, 2, 0)).getMaterial();

            if (!mc.world.getBlockState(pos).getMaterial().blocksMovement() && !mc.world.getBlockState(pos.add(0, 1, 0)).getMaterial().blocksMovement()) {
                if (renObby.getValue()) {
                    boolean solidNeighboursObby = (
                                       pos1 == Blocks.OBSIDIAN | pos1 == Blocks.BEDROCK
                                    && pos2 == Blocks.OBSIDIAN | pos2 == Blocks.BEDROCK
                                    && pos3 == Blocks.OBSIDIAN | pos3 == Blocks.BEDROCK
                                    && pos4 == Blocks.OBSIDIAN | pos4 == Blocks.BEDROCK
                                    && pos5 == Blocks.OBSIDIAN | pos5 == Blocks.BEDROCK
                                    && pos6 == Material.AIR
                                    && pos7 == Material.AIR
                                    && pos8 == Material.AIR
                    );
                    if (solidNeighboursObby) {
                        this.holesObby.add(pos);
                    }
                }
                if (renBedr.getValue()) {
                    boolean solidNeighboursBedr = (
                            pos1 == Blocks.BEDROCK
                                    && pos2 == Blocks.BEDROCK
                                    && pos3 == Blocks.BEDROCK
                                    && pos4 == Blocks.BEDROCK
                                    && pos5 == Blocks.BEDROCK
                                    && pos6 == Material.AIR
                                    && pos7 == Material.AIR
                                    && pos8 == Material.AIR
                    );
                    if (solidNeighboursBedr) {
                        this.holesBedr.add(pos);
                    }
                }
            }
        }
    }


    public void onWorldRender(RenderEvent event) {
        int colorObby = (a.getValue() & 0xff) << 24 | (r.getValue() & 0xff) << 16 | (g.getValue() & 0xff) << 8 | (b.getValue() & 0xff);
        int colorBedr = (a2.getValue() & 0xff) << 24 | (r2.getValue() & 0xff) << 16 | (g2.getValue() & 0xff) << 8 | (b2.getValue() & 0xff);

        KamiTessellator.prepare(GL11.GL_QUADS);
        this.holesObby.forEach(blockPos -> KamiTessellator.drawBox((BlockPos) blockPos, colorObby, GeometryMasks.Quad.ALL));
        this.holesBedr.forEach(blockPos -> KamiTessellator.drawBox((BlockPos) blockPos, colorBedr, GeometryMasks.Quad.ALL));
        KamiTessellator.release();
    }
}
