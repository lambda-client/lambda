package me.zeroeightsix.kami.command.syntax;

public interface SyntaxParser {
    String getChunk(SyntaxChunk[] chunks, SyntaxChunk thisChunk, String[] values, String chunkValue);
}