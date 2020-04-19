package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.gui.kami.RenderHelper;
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

import static java.lang.Math.abs;
import static me.zeroeightsix.kami.util.ColourUtils.toRGBA;
import static me.zeroeightsix.kami.util.LogUtil.getCurrentCoord;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendChatMessage;

@Module.Info(name = "Search", description = "Highlights blocks in the world", category = Module.Category.RENDER)
public class Search extends Module {
    private static final String DEFAULT_BLOCK_ESP_CONFIG = "minecraft:portal,minecraft:end_portal_frame,minecraft:bed";
    private Setting<String> espBlockNames = register(Settings.stringBuilder("blocks").withValue(DEFAULT_BLOCK_ESP_CONFIG).withConsumer((old, value) -> refreshESPBlocksSet(value)).build());
    private Setting<String> espBlockNamesAdd = register(Settings.stringBuilder("add").withValue("").withConsumer((old, value) -> addBlockToSearch(value)).build());
    private Setting<String> espBlockNamesDel = register(Settings.stringBuilder("del").withValue("").withConsumer((old, value) -> removeBlockFromSearch(value)).build());

    public void addBlockToSearch(String block) {
        espBlockNames.setValue(espBlockNames.getValue() + "," + espBlockNamesAdd.getValue());
        refreshESPBlocksSet(espBlockNames.getValue());
    }

    public void removeBlockFromSearch(String block) {
        espBlockNames.setValue(espBlockNames.getValue().replace(espBlockNamesDel.getValue() + ",", ""));
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
                    sendChatMessage(s2);
                    espBlocks.add(block);
            }
        }
    }

    private long startTime = 0;
    private ArrayList<Triplet<BlockPos, Integer, Integer>> a;

    private boolean shouldRun() {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
        if (startTime + 500 <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void makeChunks() {
        int[] pcoords = getCurrentCoord(false);
        a = new ArrayList<>();
        int renderdist = 64;
        BlockPos pos1 = new BlockPos(pcoords[0] - renderdist, 0, pcoords[2] - renderdist);
        BlockPos pos2 = new BlockPos(pcoords[0] + renderdist, 255, pcoords[2] + renderdist);
        /*BlockPos[][] smallChunks = splitChunk(pos1, pos2);
        ArrayList<ArrayList<Triplet<BlockPos, Integer, Integer>>> foundBlocks = new ArrayList<>();
        for (BlockPos[] chunk : smallChunks) {
            new Thread(() -> foundBlocks.add(findBlocksInCoords(chunk[0], chunk[1]))).start();
        }*/
        /*new Thread(() -> */findBlocksInCoords(pos1, pos2);//).start();
    }

    private BlockPos[][] splitChunk(BlockPos pos1, BlockPos pos2) {
        int x1 = pos1.getX();
        int z1 = pos1.getZ();
        int y1 = pos1.getY();
        int x2 = pos2.getX();
        int z2 = pos2.getZ();
        int y2 = pos2.getY();
        int xDist = abs(x2 - x1);
        int yDist = abs(y2 - y1);
        int zDist = abs(z2 - z1);
        int nOfChunksX = xDist/16;
        int nOfChunksY = yDist/16;
        int nOfChunksZ = zDist/16;
        int nOfChunks = nOfChunksX * nOfChunksY * nOfChunksZ;
        int startX = x1;
        int startY = z1;
        int startZ = y1;
        int currentX = x1;
        int currentY = z1;
        int currentZ = y1;
        BlockPos[][] chunks = new BlockPos[nOfChunks][2];
        int curChunk = 0;
        for (int p = 0; p < nOfChunksY; p++) {
            for (int o = 0; o < nOfChunksZ; o++) {
                for (int u = 0; u < nOfChunksX; u++) {
                    chunks[curChunk] = new BlockPos[2];
                    chunks[curChunk][0] = new BlockPos(currentX, currentY, currentZ);
                    chunks[curChunk][1] = new BlockPos(currentX + 16, currentY + 16, currentZ + 16);
                    currentX = currentX + 16;
                    curChunk++;
                }
                currentX = startX;
                currentZ = currentZ + 16;
            }
            currentY = startY;
            currentY = currentY + 16;
        }
        return chunks;
    }

    private void findBlocksInCoords(BlockPos pos1, BlockPos pos2) {
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        ArrayList<Triplet<BlockPos, Integer, Integer>> foundBlocks = new ArrayList<>();
        for (BlockPos blockPos : blocks) {
            int side = GeometryMasks.Quad.ALL;
            Block block = mc.world.getBlockState(blockPos).getBlock();
            for (Block b : espBlocks) {
                if (block == b) {
                    int c = block.blockMapColor.colorValue;
                    int[] cia = {c>>16,c>>8&255,c&255};
                    int blockColor = toRGBA(cia[0], cia[1], cia[2], 100);
                    foundBlocks.add(new Triplet<>(blockPos, blockColor, side));
                }
            }
        }
        a = foundBlocks;
    }

    @Override
    public void onWorldRender(RenderEvent event) {
        if (a != null) {
            GlStateManager.pushMatrix();
            KamiTessellator.prepare(GL11.GL_QUADS);
            for (Triplet<BlockPos, Integer, Integer> pair : a) {
                KamiTessellator.drawBox(pair.getFirst(), pair.getSecond(), pair.getThird());
            }
            KamiTessellator.release();
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
        }
    }

    public class Triplet<T, U, V> {

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

