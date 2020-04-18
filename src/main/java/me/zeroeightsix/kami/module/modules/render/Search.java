package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
        MapColor test = Blocks.CACTUS.blockMapColor;
    }

    private boolean hasMadeBlockArray = false;
    Minecraft mc = Minecraft.getMinecraft();
    private Set<Block> espBlocks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onUpdate() {
        if (!hasMadeBlockArray) {
            refreshESPBlocksSet(espBlockNames.getValue());
        }
        if (mc.player == null) return;
        if (shouldRun()) new Thread(this::calc).start();
    }

    public void onEnable() {
        refreshESPBlocksSet(espBlockNames.getValue());
    }

    private long startTime = 0;
    private ArrayList<Search.Triplet<BlockPos, Integer, Integer>> a;
    boolean doneList = false;

    private boolean shouldRun() {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
        if (startTime + 500 <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void calc() {
        a = new ArrayList<>();
        int[] pcoords = getCurrentCoord(false);
        int renderdist = 32;
        BlockPos pos1 = new BlockPos(pcoords[0] - renderdist, 0, pcoords[2] - renderdist);
        BlockPos pos2 = new BlockPos(pcoords[0] + renderdist, 255, pcoords[2] + renderdist);
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        for (BlockPos blockPos : blocks) {
            int side = GeometryMasks.Quad.ALL;
            Block block = mc.world.getBlockState(blockPos).getBlock();
            for (Block b : espBlocks) {
                if (b == block) {
                    int c = block.blockMapColor.colorValue;
                    int[] cia = {c>>16,c>>8&255,c&255};
                    int blockColor = toRGBA(cia[0], cia[1], cia[2], 100);
                    a.add(new Search.Triplet<>(blockPos, blockColor, side));
                }
            }
        }
        doneList = true;
    }

    private void refreshESPBlocksSet(String v) {
        espBlocks.clear();
        for (String s : v.split(",")) {
            String s2 = s.trim();
            Block block = Block.getBlockFromName(s2);
            if (block != null)
                espBlocks.add(block);
        }
        hasMadeBlockArray = true;
    }

    @Override
    public void onWorldRender(RenderEvent event) {
        if (doneList && mc.player != null) {
            GlStateManager.pushMatrix();
            KamiTessellator.prepare(GL11.GL_QUADS);
            for (Search.Triplet<BlockPos, Integer, Integer> pair : a)
                KamiTessellator.drawBox(pair.getFirst(), pair.getSecond(), pair.getThird());
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

