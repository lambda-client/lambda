package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static me.zeroeightsix.kami.util.ColourUtils.toRGBA;
import static me.zeroeightsix.kami.util.LogUtil.getCurrentCoord;

@Module.Info(name = "Search", description = "Highlights blocks in the world", category = Module.Category.RENDER)
public class Search extends Module {
    private static final String DEFAULT_BLOCK_ESP_CONFIG = "minecraft:portal,minecraft:end_portal_frame,minecraft:bed,";
    private Setting<String> espBlockNames = register(Settings.stringBuilder("blocks").withValue(DEFAULT_BLOCK_ESP_CONFIG).withConsumer((old, value) -> refreshESPBlocksSet(value)).build());
    private Setting<String> espBlockNamesAdd = register(Settings.stringBuilder("add").withValue("").withConsumer((old, value) -> addBlockToSearch(value)).build());
    private Setting<String> espBlockNamesDel = register(Settings.stringBuilder("del").withValue("").withConsumer((old, value) -> removeBlockFromSearch(value)).build());

    public void addBlockToSearch(String block) {
        espBlockNames.setValue(espBlockNames.getValue() + block + ",");
        refreshESPBlocksSet(espBlockNames.getValue());
    }

    public void removeBlockFromSearch(String block) {
        espBlockNames.setValue(espBlockNames.getValue().replace(block + ",", ""));
        refreshESPBlocksSet(espBlockNames.getValue());
    }

    Minecraft mc = Minecraft.getMinecraft();
    private Set<Block> espBlocks;

    @Override
    public void onUpdate() {
        if (espBlocks == null) {
            refreshESPBlocksSet(espBlockNames.getValue());
        }
        if (mc.player == null) return;
        if (shouldRun()) new Thread(this::makeChunks).start();
    }

    public void onEnable() {
        refreshESPBlocksSet(espBlockNames.getValue());
    }

    private void refreshESPBlocksSet(String v) {
        espBlocks = Collections.synchronizedSet(new HashSet<>());
        for (String s : v.split(",")) {
            String s2 = s.trim();
            if (!s2.equals("minecraft:air")) {
                Block block = Block.getBlockFromName(s2);
                if (block != null)
                    espBlocks.add(block);
            }
        }
    }

    private long startTime = 0;
    private ArrayList<ArrayList<Triplet<BlockPos, Integer, Integer>>> a;

    private boolean shouldRun() {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
        if (startTime + 500 <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    boolean doneList = false;

    private void makeChunks() {
        doneList = false;
        int[] pcoords = getCurrentCoord(false);
        int renderdist = mc.gameSettings.renderDistanceChunks * 16;
        if (renderdist > 80) {
            renderdist = 80;
        }
        BlockPos pos1 = new BlockPos(pcoords[0] - renderdist, 0, pcoords[2] - renderdist);
        BlockPos pos2 = new BlockPos(pcoords[0] + renderdist, 256, pcoords[2] + renderdist);
        ArrayList<ArrayList<Triplet<BlockPos, Integer, Integer>>> foundBlocks = new ArrayList<>();
        foundBlocks.add(findBlocksInCoords(pos1, pos2, espBlocks));
        a = foundBlocks;
        doneList = true;
    }

    private ArrayList<Triplet<BlockPos, Integer, Integer>> findBlocksInCoords(BlockPos pos1, BlockPos pos2, Set<Block> blocksToFind) {
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        ArrayList<Triplet<BlockPos, Integer, Integer>> foundBlocks = new ArrayList<>();
        for (BlockPos blockPos : blocks) {
            int side = GeometryMasks.Quad.ALL;
            Block block = mc.world.getBlockState(blockPos).getBlock();
            for (Block b : blocksToFind) {
                if (block == b) {
                    int c = block.blockMapColor.colorValue;
                    int[] cia = {c>>16,c>>8&255,c&255};
                    int blockColor = toRGBA(cia[0], cia[1], cia[2], 100);
                    foundBlocks.add(new Triplet<>(blockPos, blockColor, side));
                }
            }
        }
        return foundBlocks;
    }

    ArrayList<ArrayList<Triplet<BlockPos, Integer, Integer>>> blocksToShow;
    @Override
    public void onWorldRender(RenderEvent event) {
        if (doneList && a != null) {
            blocksToShow = a;
        }
        if (blocksToShow != null) {
            GlStateManager.pushMatrix();
            KamiTessellator.prepare(GL11.GL_QUADS);
            for (ArrayList<Triplet<BlockPos, Integer, Integer>> blockList : blocksToShow) {
                for (Triplet<BlockPos, Integer, Integer> pair : blockList)
                    KamiTessellator.drawBox(pair.getFirst(), pair.getSecond(), pair.getThird());
            }
            KamiTessellator.release();
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
        }
    }

    public static class Triplet<T, U, V> {

        private final T first;
        private final U second;
        private final V third;

        public Triplet(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        public T getFirst() {
            return first;
        }

        public U getSecond() {
            return second;
        }

        public V getThird() {
            return third;
        }
    }
}

