package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketChatMessage;

import static me.zeroeightsix.kami.KamiMod.*;

/**
 * Created by 086 on 8/04/2018.
 * Updated by S-B99 on 26/01/20
 */
@Module.Info(name = "CustomChat", category = Module.Category.MISC, description = "Chat ending", showOnArray = Module.ShowOnArray.OFF)
public class CustomChat extends Module {

    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    private Setting<TextMode> textMode = register(Settings.e("Content", TextMode.JAPANESE));
    private Setting<DecoMode> decoMode = register(Settings.e("Punctuation", DecoMode.CLASSIC));
    private Setting<Boolean> commands = register(Settings.b("Commands", false));

    private enum TextMode {
        NAME, ONTOP, WEBSITE, JAPANESE;
    }

    private enum DecoMode {
        SEPARATOR, CLASSIC, NONE
    }

    private String getText(TextMode t) {
        switch (t) {
            case NAME: return KAMI_BLUE;
            case ONTOP: return KAMI_ONTOP;
            case WEBSITE: return KAMI_WEBSITE;
            case JAPANESE: return KAMI_JAPANESE_ONTOP;
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
            if (!commands.getValue()) {
                if (s.startsWith("/")) return;
                else if (s.startsWith(",")) return;
                else if (s.startsWith(".")) return;
                else if (s.startsWith("-")) return;
                else if (s.startsWith(";")) return;
                else if (s.startsWith("?")) return;
                else if (s.startsWith("*")) return;
                else if (s.startsWith("^")) return;
                else if (s.startsWith("&")) return;
            }
            s += getFull(decoMode.getValue());
            if (s.length() >= 256) s = s.substring(0, 256);
            ((CPacketChatMessage) event.getPacket()).message = s;
        }
    });

}
