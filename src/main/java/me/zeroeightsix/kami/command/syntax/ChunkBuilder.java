package me.zeroeightsix.kami.command.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 086 on 12/11/2017.
 */
public class ChunkBuilder {

    private static final SyntaxChunk[] EXAMPLE = SyntaxChunk.EMPTY;
    private List<SyntaxChunk> chunks = new ArrayList<>();

    public ChunkBuilder append(SyntaxChunk syntaxChunk) {
        chunks.add(syntaxChunk);
        return this;
    }

    public ChunkBuilder append(String head, boolean necessary) {
        append(new SyntaxChunk(head, necessary));
        return this;
    }

    public ChunkBuilder append(String head, boolean necessary, SyntaxParser parser) {
        SyntaxChunk chunk = new SyntaxChunk(head, necessary);
        chunk.setParser(parser);
        append(chunk);
        return this;
    }

    public SyntaxChunk[] build() {
        return chunks.toArray(EXAMPLE);
    }

    public ChunkBuilder append(String name) {
        return append(name, true);
    }
}
