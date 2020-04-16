package me.zeroeightsix.kami.command.syntax;

public class SyntaxChunk {

    private boolean headless = false;
    private String head; // Null if headless
    private String type;
    private boolean necessary;
    private SyntaxParser parser;

    public static final SyntaxChunk[] EMPTY = new SyntaxChunk[]{};

    public SyntaxChunk(String head, String type, boolean necessary) {
        this.head = head;
        this.type = type;
        this.necessary = necessary;
        this.parser = (chunks, thisChunk, values, chunkValue) -> {
            if (chunkValue != null) return null;
            return head + (isNecessary() ? "<" : "[") + type + (isNecessary() ? ">" : "]");
        };
    }

    public SyntaxChunk(String type, boolean necessary) {
        this("", type, necessary);
        this.headless = true;
    }

    public String getHead() {
        return head;
    }

    public boolean isHeadless() {
        return headless;
    }

    public boolean isNecessary() {
        return necessary;
    }

    public String getChunk(SyntaxChunk[] chunks, SyntaxChunk thisChunk, String[] args, String chunkValue) {
        String s = parser.getChunk(chunks, thisChunk, args, chunkValue);
        if (s == null) return "";
        return s;
    }

    public String getType() {
        return type;
    }

    public void setParser(SyntaxParser parser) {
        this.parser = parser;
    }

}
