package me.zeroeightsix.kami.command.syntax.parsers;

import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class BlockParser extends AbstractParser {

    private static HashMap<String, Block> blockNames = new HashMap<>();

    public BlockParser() {
        if (!blockNames.isEmpty()) return;
        for (ResourceLocation resourceLocation : Block.REGISTRY.getKeys()) {
            blockNames.put(resourceLocation.toString().replace("minecraft:", "").replace("_", ""), Block.REGISTRY.getObject(resourceLocation));
        }
    }

    @Override
    public String getChunk(SyntaxChunk[] chunks, SyntaxChunk thisChunk, String[] values, String chunkValue) {
        try {
            if (chunkValue == null)
                return (thisChunk.isHeadless() ? "" : thisChunk.getHead()) + (thisChunk.isNecessary() ? "<" : "[") + thisChunk.getType() + (thisChunk.isNecessary() ? ">" : "]");

            HashMap<String, Block> possibilities = new HashMap<>();

            for (String s : blockNames.keySet()) {
                if (s.toLowerCase().startsWith(chunkValue.toLowerCase().replace("minecraft:", "").replace("_", "")))
                    possibilities.put(s, blockNames.get(s));
            }

            if (possibilities.isEmpty()) return "";

            TreeMap<String, Block> p = new TreeMap<>(possibilities);

            Map.Entry<String, Block> e = p.firstEntry();
            return e.getKey().substring(chunkValue.length());
        } catch (Exception e) {
            return "";
        }
    }
}
