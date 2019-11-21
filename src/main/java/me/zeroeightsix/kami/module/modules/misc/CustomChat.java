package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketChatMessage;

import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiilllll.lllllllliiiliiilllll;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliiiii.lllllllliiiliiliiiii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliiiil.lllllllliiiliiliiiil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliiili.lllllllliiiliiliiili;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliiill.lllllllliiiliiliiill;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliilii.lllllllliiiliiliilii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliilil.lllllllliiiliiliilil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliilli.lllllllliiiliiliilli;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliiliilll.lllllllliiiliiliilll;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliililiii.lllllllliiiliililiii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliililiil.lllllllliiiliililiil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliililili.lllllllliiiliililili;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliililill.lllllllliiiliililill;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilillii.lllllllliiiliilillii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilillil.lllllllliiiliilillil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilillli.lllllllliiiliilillli;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilillll.lllllllliiiliilillll;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilliiii.lllllllliiiliilliiii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilliiil.lllllllliiiliilliiil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilliili.lllllllliiiliilliili;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilliill.lllllllliiiliilliill;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillilii.lllllllliiiliillilii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillilil.lllllllliiiliillilil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillilli.lllllllliiiliillilli;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillilll.lllllllliiiliillilll;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillliii.lllllllliiiliillliii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillliil.lllllllliiiliillliil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilllili.lllllllliiiliilllili;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliilllill.lllllllliiiliilllill;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillllii.lllllllliiiliillllii;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillllil.lllllllliiiliillllil;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillllli.lllllllliiiliillllli;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiiliillllll.lllllllliiiliillllll;
import static me.zeroeightsix.kami.module.modules.sdashb.lllllllliiiliiilllli.lllllllliiililiiiiii.lllllllliiililiiiiii;

/**
 * Created by 086 on 8/04/2018.
 * Updated by S-B99 on 12/11/19
 */
@Module.Info(name = "CustomChat", category = Module.Category.MISC, description = "Chat ending. Now has modes!")
public class CustomChat extends Module {

    private Setting<TextMode> textMode = register(Settings.e("Text", TextMode.WEBSITE));
    private Setting<DecoMode> decoMode = register(Settings.e("Decoration", DecoMode.CLASSIC));
    private Setting<Boolean> commands = register(Settings.b("Commands", false));

    private String KAMI_SEPARATOR = " " + lllllllliiiliiilllll + " ";
    private String KAMI_CLASSIC = " " + lllllllliiiliiliiiii + " ";
    private String KAMI_CLASSIC_OTHER = " " + lllllllliiiliiliiili;
    private String KAMI_NAME = lllllllliiiliiliiiil + lllllllliiiliiliiill + lllllllliiiliiliilii + lllllllliiiliiliilli + " " + lllllllliiiliiliilil + lllllllliiiliiliilll + lllllllliiiliililiii + lllllllliiiliililiil;
    private String KAMI_ONTOP = lllllllliiiliiliiiil + lllllllliiiliiliiill + lllllllliiiliiliilii + lllllllliiiliiliilli + " " + lllllllliiiliiliilil + lllllllliiiliiliilll + lllllllliiiliililiii + lllllllliiiliililiil + " " + lllllllliiiliililili + lllllllliiiliililill + " " + lllllllliiiliilillii + lllllllliiiliilillil + lllllllliiiliilillli;
    private String KAMI_WEBSITE = lllllllliiiliilillll + lllllllliiiliilliiii + lllllllliiiliilliiil + lllllllliiiliilliili + lllllllliiiliilliill + lllllllliiiliillilii + lllllllliiiliillilil + lllllllliiiliillilli + lllllllliiiliillilll + lllllllliiiliillliii + lllllllliiiliillliil + lllllllliiiliilllili + lllllllliiiliilllill + lllllllliiiliillllii + lllllllliiiliillllil + lllllllliiiliillllli + lllllllliiiliillllll + lllllllliiililiiiiii;
    private String KAMI_FINAL = "";
    private String KAMI_ALL = " \u23d0 \u166d\uff4f\u1587\uff0d\u1455\u14aa\uff49\u4e47\u144e\u3112 \u23d0 \u1d1b\u0280\u026a\u1d18\u029f\ua731\u02e2\u026a\u02e3 \u23d0 \u0e23\u0e4f\u0e22\u05e7\u0452\u0e04\u03c2\u043a \u23d0 \u0050\u0045\u004e\u0049\u0053 \u23d0 \u0274\u1d1c\u1d1b\u0262\u1d0f\u1d05\u002e\u1d04\u1d04 \u0fc9 \u23d0 \u1d0b\u1d07\u1d07\u1d0d\u028f\u002e\u1d04\u1d04\u30c4 \u23d0 \u0493\u1d1c\u0280\u0280\u028f\u1d21\u1d00\u0280\u1d07 \u23d0 \u0262\u1d00\u028f \u23d0 \u1d07\u029f\u1d07\u1d0d\u1d07\u0274\u1d1b\u1d00\ua731\u002e\u1d04\u1d0f\u1d0d \u23d0 \u0299\u1d00\u029f\u1d05\u029c\u1d00\u1d04\u1d0b \u2713\u1d00\u1d18\u0150\u029f\u00a5\u028f\u1d0f\u0143\u002e\u0493\u1d00\u0262 \u00bb\u0299\u1d00\u1d04\u1d0b\u1d05\u1d0f\u1d0f\u0280\u1d07\u1d05 \u23d0 \u0030\u0032\u0037\u0048\u0061\u0063\u006b \u23d0 \u1d00\u1d04\u1d07 \u029c\u1d00\u1d04\u1d0b \u23d0 ";

    @Override
    public void onUpdate() {
        if (textMode.getValue().equals(TextMode.ALL)) {
            Command.sendChatMessage("[CustomChat] Note: ALL text mode only works with the separator decoration mode");
        }
    }
    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String s = ((CPacketChatMessage) event.getPacket()).getMessage();
            if (!commands.getValue()) {
                if (s.startsWith("/")) {
                    return;
                } else if (s.startsWith(",")) {
                    return;
                } else if (s.startsWith(".")) {
                    return;
                } else if (s.startsWith("-")) {
                    return;
                }
            }
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
                else if (textMode.getValue().equals(TextMode.ALL)) {
                    KAMI_FINAL = KAMI_ALL + KAMI_NAME;
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
        NAME, ONTOP, WEBSITE, ALL
    }
    private enum DecoMode {
        SEPARATOR, CLASSIC, NONE

    }

}
