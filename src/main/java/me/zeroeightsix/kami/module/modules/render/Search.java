package me.zeroeightsix.kami.module.modules.render;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static me.zeroeightsix.kami.util.ColourUtils.changeAlpha;
import static me.zeroeightsix.kami.util.ColourUtils.toRGBA;
import static me.zeroeightsix.kami.util.LogUtil.getCurrentCoord;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendErrorMessage;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendWarningMessage;

/**
 * @author wnuke
 * Updated by dominikaaaa on 20/04/20
 */
@Module.Info(
        name = "Search",
        description = "Highlights blocks in the world",
        category = Module.Category.RENDER
)
public class Search extends Module {
    private Setting<Integer> alpha = register(Settings.integerBuilder("Transparency").withMinimum(1).withMaximum(255).withValue(120).build());
    public Setting<Boolean> overrideWarning = register(Settings.booleanBuilder("overrideWarning").withValue(false).withVisibility(v -> false));
    Minecraft mc = Minecraft.getMinecraft();
    private Set<Block> espBlocks;
    private static final String DEFAULT_BLOCK_ESP_CONFIG = "minecraft:portal, minecraft:end_portal_frame, minecraft:bed";
    private Setting<String> espBlockNames = register(Settings.stringBuilder("HiddenBlocks").withValue(DEFAULT_BLOCK_ESP_CONFIG).withConsumer((old, value) -> refreshESPBlocksSet(value)).build());

    public String extGet() {
        return extGetInternal(null);
    }

    // Add entry by arbitrary user-provided string
    public void extAdd(String s) {
        espBlockNames.setValue(extGetInternal(null) + ", " + s);
    }

    // Remove entry by arbitrary user-provided string
    public void extRemove(String s) {
        espBlockNames.setValue(extGetInternal(Block.getBlockFromName(s)));
    }

    // Clears the list.
    public void extClear() {
        espBlockNames.setValue("");
    }

    // Resets the list to default
    public void extDefaults() {
        extClear();
        extAdd(DEFAULT_BLOCK_ESP_CONFIG);
    }

    // Set the list to 1 value
    public void extSet(String s) {
        extClear();
        extAdd(s);
    }

    private String extGetInternal(Block filter) {
        StringBuilder sb = new StringBuilder();
        boolean notFirst = false;
        for (Block b : espBlocks) {
            if (b == filter)
                continue;
            if (notFirst)
                sb.append(", ");
            notFirst = true;
            sb.append(Block.REGISTRY.getNameForObject(b));
        }
        return sb.toString();
    }

    Executor exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void onUpdate() {
        if (espBlocks == null) {
            refreshESPBlocksSet(espBlockNames.getValue());
        }
        if (mc.player == null) return;
        if (shouldRun()) makeChunks();
    }

    public void onEnable() {
        if (!overrideWarning.getValue() && GlStateManager.glGetString(7936).contains("Intel")) {
            sendErrorMessage(getChatName() + "Warning: Running Search with an Intel Integrated GPU is not supported, as it has a &lHUGE&r impact on performance.");
            sendWarningMessage(getChatName() + "If you're sure you want to try, run the &7" + Command.getCommandPrefix() + "search override&f command");
            disable();
            return;
        }
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
    private final Map<BlockPos, Tuple<Integer, Integer>> a = new HashMap<>();

    private boolean shouldRun() {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
        if (startTime + 500 <= System.currentTimeMillis()) { // 1 timeout = 1 second = 1000 ms
            startTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }


    final AtomicBoolean doneList = new AtomicBoolean(false);
    final AtomicInteger runningThreadCount = new AtomicInteger();

    /*@EventHandler
    public Listener<ChunkEvent.Load> listener = new Listener<>(event -> {
            if(doneList.compareAndSet(true,false)){
                Chunk chunk = event.getChunk();
                ChunkPos pos = chunk.getPos();
                KamiMod.log.info("[ SEARCH ] loaded chunk: " + pos.x + "," + pos.z);
                BlockPos pos1 = new BlockPos(pos.getXStart(), 0,pos.getZStart());
                BlockPos pos2 = new BlockPos(pos.getXEnd(), 256,pos.getZEnd());
                a.putAll(findBlocksInCoords(pos1, pos2, espBlocks));
                doneList.compareAndSet(false,true);
            }
    });*/

    private void makeChunks() {
        if(runningThreadCount.get() > 0) return;
        int slices = Runtime.getRuntime().availableProcessors();
        int y_gap = 256 / slices;
        for (int i = 0; i < slices; i++) {
            int finalI = i;
            exec.execute(() ->
                    _makeChunks(
                            finalI * y_gap,
                            ((finalI + 1) * y_gap) - 1)
            );
        }
    }

    private void _makeChunks(int bottom_y, int top_y) {
        int thread;
        if ((thread = runningThreadCount.getAndIncrement()) == 0) {
            doneList.set(false);
            a.clear();
        }
        KamiMod.log.debug("[SEARCH] thread " + (thread + 1));
        int[] pcoords = getCurrentCoord(false);
        int renderdist = mc.gameSettings.renderDistanceChunks * 16;
        if (renderdist > 80) {
            renderdist = 80;
        }
        BlockPos pos1 = new BlockPos(pcoords[0] - renderdist, bottom_y, pcoords[2] - renderdist);
        BlockPos pos2 = new BlockPos(pcoords[0] + renderdist, top_y, pcoords[2] + renderdist);
        a.putAll(findBlocksInCoords(pos1, pos2, espBlocks));
        if (runningThreadCount.decrementAndGet() == 0)
            doneList.set(true);
    }

    private Map<BlockPos, Tuple<Integer, Integer>> findBlocksInCoords(BlockPos pos1, BlockPos pos2, Set<Block> blocksToFind) {
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        Map<BlockPos, Tuple<Integer, Integer>> foundBlocks = new HashMap<>();
        for (BlockPos blockPos : blocks) {
            int side = GeometryMasks.Quad.ALL;
            Block block = mc.world.getBlockState(blockPos).getBlock();
            if (blocksToFind.contains(block)) {
                int c = block.blockMapColor.colorValue;
                int[] cia = {c >> 16, c >> 8 & 255, c & 255};
                int blockColor = toRGBA(cia[0], cia[1], cia[2], alpha.getValue());
                foundBlocks.put(blockPos, new Tuple<>(blockColor, side));
            }
        }
        return foundBlocks;
    }

    Map<BlockPos, Tuple<Integer, Integer>> blocksToShow;

    @Override
    public void onWorldRender(RenderEvent event) {
        if (doneList.get() && a != null) {
            blocksToShow = new HashMap<>(a);
        }
        if (blocksToShow != null) {
            GlStateManager.pushMatrix();
            KamiTessellator.prepare(GL11.GL_QUADS);
            for (Map.Entry<BlockPos, Tuple<Integer, Integer>> entry : blocksToShow.entrySet()) {
                KamiTessellator.drawBox(entry.getKey(), entry.getValue().getFirst(), entry.getValue().getSecond());
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

