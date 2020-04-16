package me.zeroeightsix.kami.command;

import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;

public abstract class Command {

    public static Setting<String> commandPrefix = Settings.s("commandPrefix", ";");
    public final Minecraft mc = Minecraft.getMinecraft();
    protected String label;
    protected String syntax;
    protected String description;
    protected List<String> aliases;
    protected SyntaxChunk[] syntaxChunks;

    public Command(String label, SyntaxChunk[] syntaxChunks, String... aliases) {
        this.label = label;
        this.syntaxChunks = syntaxChunks;
        this.description = "Descriptionless";
        this.aliases = Arrays.asList(aliases);
    }

    public static String getCommandPrefix() {
        return commandPrefix.getValue();
    }

    public String getDescription() {
        return description;
    }

    protected void setDescription(String description) {
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getChatLabel() {
        return "[" + label + "] ";
    }

    public abstract void call(String[] args);

    public SyntaxChunk[] getSyntaxChunks() {
        return syntaxChunks;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
