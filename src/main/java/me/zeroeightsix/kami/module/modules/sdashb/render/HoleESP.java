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
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

/***
 * @author S-B99
 * Created by @S-B99 on 30/11/19
 */
@Module.Info(name = "HoleESP", category = Module.Category.RENDER)
public class HoleESP extends Module {

    private ArrayList<BlockPos> holesObby = new ArrayList();
    private ArrayList<BlockPos> holesBedr = new ArrayList();
    private static long startTime = 0;
    private static long startTimeTwo = 0;

    private Setting<Boolean> renObby = register(Settings.b("Render Obby", true));
    private Setting<Boolean> renBedr = register(Settings.b("Render Bedrock", true));
    //    private Setting<Double> speed = register(Settings.doubleBuilder().withName("Calculate speed").withMinimum(0.1).withValue(3.0).withMaximum(10.0).build());
    private Setting<Integer> distance = register(Settings.integerBuilder().withName("Distance").withMinimum(1).withValue(5).withMaximum(20).build());
    private Setting<Integer> a = register(Settings.integerBuilder("Transparency (Obby)").withMinimum(0).withValue(26).withMaximum(255).build());
    private Setting<Integer> r = register(Settings.integerBuilder("Red (Obby)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> g = register(Settings.integerBuilder("Green (Obby)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> b = register(Settings.integerBuilder("Blue (Obby)").withMinimum(0).withValue(255).withMaximum(255).build());
    private Setting<Integer> a2 = register(Settings.integerBuilder("Transparency (Bedrock)").withMinimum(0).withValue(26).withMaximum(255).build());
    private Setting<Integer> r2 = register(Settings.integerBuilder("Red (Bedrock)").withMinimum(0).withValue(208).withMaximum(255).build());
    private Setting<Integer> g2 = register(Settings.integerBuilder("Green (Bedrock)").withMinimum(0).withValue(144).withMaximum(255).build());
    private Setting<Integer> b2 = register(Settings.integerBuilder("Blue (Bedrock)").withMinimum(0).withValue(255).withMaximum(255).build());

    public void onUpdate() {
        if (mc.player == null) return;

        Iterable<BlockPos> blocks = null;
        this.holesObby = new ArrayList();
        this.holesBedr = new ArrayList();

//        double delayTime = 100.0 * speed.getValue();
//        double delayTimeTwo = 100.0 * speed.getValue();

        blocks = BlockPos.getAllInBox(mc.player.getPosition().add(-distance.getValue(), -distance.getValue(), -distance.getValue()), mc.player.getPosition().add(distance.getValue(), distance.getValue(), distance.getValue()));

        if (blocks == null) { // prevent rare crash cases
            Command.sendErrorMessage("[HoleESP] Caught NPE, contact S-B99 about this");
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
//                    if (startTime == 0) startTime = System.currentTimeMillis();
//                    if (startTime + delayTime <= System.currentTimeMillis()) {
                    if (solidNeighboursObby) {
//                            Command.sendWarningMessage("[HoleESP] Ran this 2");
                        this.holesObby.add(pos);
                    }
//                        startTime = System.currentTimeMillis();
//                    }
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
//                    if (startTimeTwo == 0) startTimeTwo = System.currentTimeMillis();
//                    if (startTimeTwo + delayTimeTwo <= System.currentTimeMillis()) {
                    if (solidNeighboursBedr) {
//                            Command.sendWarningMessage("[HoleESP] Ran this");
                        this.holesBedr.add(pos);
                    }
//                        startTimeTwo = System.currentTimeMillis();
//                    }
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
