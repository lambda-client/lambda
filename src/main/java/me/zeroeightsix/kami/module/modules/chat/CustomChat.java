package me.zeroeightsix.kami.module.modules.chat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketChatMessage;

import static me.zeroeightsix.kami.KamiMod.*;

/**
 * Created by 086 on 8/04/2018.
 * Updated by S-B99 on 12/03/20
 */
@Module.Info(name = "CustomChat", category = Module.Category.CHAT, description = "Adds a watermark to the end of your message to let others know you're using KAMI Blue", showOnArray = Module.ShowOnArray.OFF)
public class CustomChat extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    public Setting<TextMode> textMode = register(Settings.e("Message", TextMode.ON_TOP));
    private Setting<DecoMode> decoMode = register(Settings.e("Separator", DecoMode.NONE));
    private Setting<Boolean> commands = register(Settings.b("Commands", false));
    public Setting<String> customText = register(Settings.stringBuilder("Custom Text").withValue("unchanged").withConsumer((old, value) -> {}).build());

    private enum DecoMode { SEPARATOR, CLASSIC, NONE }
    public enum TextMode { NAME, ON_TOP, WEBSITE, JAPANESE, CUSTOM }
    public static String[] cmdCheck = new String[]{"/", ",", ".", "-", ";", "?", "*", "^", "&", Command.getCommandPrefix()};

    private String getText(TextMode t) {
        switch (t) {
            case NAME: return KAMI_BLUE;
            case ON_TOP: return KAMI_ONTOP;
            case WEBSITE: return KAMI_WEBSITE;
            case JAPANESE: return KAMI_JAPANESE_ONTOP;
            case CUSTOM: return customText.getValue();
            default: return "";
        }
    }

    private String getFull(DecoMode d) {
        switch (d) {
            case NONE: return " " + getText(textMode.getValue());
            case CLASSIC: return  " " + quoteLeft + " " + getText(textMode.getValue()) + " " + quoteRight;
            case SEPARATOR: return " " + separator + " " + getText(textMode.getValue());
            default: return "";
        }
    }

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String s = ((CPacketChatMessage) event.getPacket()).getMessage();
            if (!commands.getValue() && isCommand(s)) return;
            s += getFull(decoMode.getValue());
            if (s.length() >= 256) s = s.substring(0, 256);
            ((CPacketChatMessage) event.getPacket()).message = s;
        }
    });

    private boolean isCommand(String s) {
        for (String value : cmdCheck) {
            if (s.startsWith(value)) return true;
        }
        return false;
    }

    private static long startTime = 0;
    @Override
    public void onUpdate() {
        if (startTime == 0) startTime = System.currentTimeMillis();
        if (startTime + 5000 <= System.currentTimeMillis()) { // 5 seconds in milliseconds
            if (textMode.getValue().equals(TextMode.CUSTOM) && customText.getValue().equalsIgnoreCase("unchanged") && mc.player != null) {
                Command.sendWarningMessage(getChatName() + " Warning: In order to use the custom " + getName() + ", please run the &7" + Command.getCommandPrefix() + "customchat&r command to change it");
            }
            startTime = System.currentTimeMillis();
        }
    }

    public void onDisable() { Command.sendAutoDisableMessage(getName(), startupGlobal.getValue()); }
}
