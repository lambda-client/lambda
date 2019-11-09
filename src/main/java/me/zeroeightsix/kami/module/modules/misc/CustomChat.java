package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.modules.combat.Aura;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketChatMessage;

/**
 * Created by 086 on 8/04/2018.
 * Updated by S-B99 on 28/10/19
 */
@Module.Info(name = "CustomChat", category = Module.Category.MISC, description = "Chat ending. Now has modes!")
public class CustomChat extends Module {

    private Setting<TextMode> textMode = register(Settings.e("Text", TextMode.NAME));
    private Setting<DecoMode> decoMode = register(Settings.e("Decoration", DecoMode.SEPARATOR));
    
    private String KAMI_SEPARATOR = " \u23d0 ";
    private String KAMI_CLASSIC = " \u00ab ";
    private String KAMI_CLASSIC_OTHER = " \u00bb";
    private String KAMI_NAME = "\u1d0b\u1d00\u1d0d\u026a \u0299\u029f\u1d1c\u1d07";
    private String KAMI_ONTOP = "\u1d0b\u1d00\u1d0d\u026a \u0299\u029f\u1d1c\u1d07 \u1d0f\u0274 \u1d1b\u1d0f\u1d18";
    private String KAMI_WEBSITE = "\u0299\u1d07\u029f\u029f\u1d00\u002e\u1d21\u1d1b\ua730\u002f\u1d0b\u1d00\u1d0d\u026a\u0299\u029f\u1d1c\u1d07";
    private String KAMI_FINAL = "";

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String s = ((CPacketChatMessage) event.getPacket()).getMessage();
            if (s.startsWith("/"))
            	return;
            else if (s.startsWith(","))
            	return;
            else if (s.startsWith("."))
            	return;
            else if (s.startsWith("-"))
            	return;
            // TODO: reset the classic mode so it doesn't add
            if (decoMode.getValue().equals(DecoMode.SEPARATOR)) {
                if (textMode.getValue().equals(TextMode.NAME)) {
                    KAMI_FINAL = KAMI_SEPARATOR + KAMI_NAME;
                }
                else if (textMode.getValue().equals(TextMode.ONTOP)) {
                    KAMI_FINAL = KAMI_SEPARATOR + KAMI_ONTOP;
                }
                else if (textMode.getValue().equals(TextMode.WEBSITE)) {
                    KAMI_FINAL = KAMI_SEPARATOR + KAMI_WEBSITE;
                }
            }
            else if (decoMode.getValue().equals(DecoMode.NONE)) {
                if (textMode.getValue().equals(TextMode.NAME)) {
                    KAMI_FINAL = " " + KAMI_NAME;
                }
                else if (textMode.getValue().equals(TextMode.ONTOP)) {
                    KAMI_FINAL = " " + KAMI_ONTOP;
                }
                else if (textMode.getValue().equals(TextMode.WEBSITE)) {
                    KAMI_FINAL = " " + KAMI_WEBSITE;
                }
            }
            else if (decoMode.getValue().equals(DecoMode.CLASSIC)) {
                if (textMode.getValue().equals(TextMode.NAME)) {
                    KAMI_FINAL = KAMI_CLASSIC + KAMI_NAME + KAMI_CLASSIC_OTHER;
                }
                else if (textMode.getValue().equals(TextMode.ONTOP)) {
                    KAMI_FINAL = KAMI_CLASSIC + KAMI_ONTOP + KAMI_CLASSIC_OTHER;
                }
                else if (textMode.getValue().equals(TextMode.WEBSITE)) {
                    KAMI_FINAL = KAMI_CLASSIC + KAMI_WEBSITE + KAMI_CLASSIC_OTHER;
                }
            }
                s += KAMI_FINAL;
            if (s.length() >= 256) s = s.substring(0,256);
            ((CPacketChatMessage) event.getPacket()).message = s;
        }
    });

    private enum TextMode {
        NAME, ONTOP, WEBSITE
    }
    private enum DecoMode {
        SEPARATOR, CLASSIC, NONE

    }

}
