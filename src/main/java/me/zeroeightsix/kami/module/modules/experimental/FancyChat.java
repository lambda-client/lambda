package me.zeroeightsix.kami.module.modules.experimental;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.modules.chat.CustomChat;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketChatMessage;

import java.util.Random;

import static me.zeroeightsix.kami.util.InfoCalculator.isNumberEven;

/**
 * @author S-B99
 * Updated by S-B99 on 12/03/20
 */
@Module.Info(name = "FancyChat", category = Module.Category.EXPERIMENTAL, description = "Makes messages you send fancy", showOnArray = Module.ShowOnArray.OFF)
public class FancyChat extends Module {
    private Setting<Mode> modeSetting = register(Settings.e("Mode", Mode.MOCKING));
    private Setting<Boolean> randomSetting = register(Settings.booleanBuilder("Random Case").withValue(true).withVisibility(v -> modeSetting.getValue().equals(Mode.MOCKING)).build());
    private Setting<Boolean> commands = register(Settings.b("Commands", false));

    private enum Mode { UWU, LEET, MOCKING }
    private static Random random = new Random();

    private String getText(Mode t, String s) {
        switch (t) {
            case UWU:
                return "Error: UWU mode is still experimental";
            case LEET:
                return leetConverter(s);
            case MOCKING:
                return mockingConverter(s);
            default:
                return "";
        }
    }

    // rules:
    // ove > uv
    // l > w
    //
//    private String uwuifier(String input) {
//        input.replace("a", "b");
//    }

    private String leetConverter(String input) {
        StringBuilder message = new StringBuilder();
        for (int i = 0 ; i < input.length() ; i++) {
            String inputChar = input.charAt(i) + "";
            inputChar = inputChar.toLowerCase();
            inputChar = leetSwitch(inputChar);
            message.append(inputChar);
        }
        return message.toString();
    }

    private String mockingConverter(String input) {
        StringBuilder message = new StringBuilder();
        for (int i = 0 ; i < input.length() ; i++) {
            String inputChar = input.charAt(i) + "";

            int rand = 0;
            if (randomSetting.getValue()) rand = random.nextBoolean() ? 1 : 0;

            if (!isNumberEven(i + rand)) inputChar = inputChar.toUpperCase();
            else inputChar = inputChar.toLowerCase();
            message.append(inputChar);
        }
        return message.toString();
    }

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String s = ((CPacketChatMessage) event.getPacket()).getMessage();
            if (!commands.getValue() && isCommand(s)) return;
            s = getText(modeSetting.getValue(), s);
            if (s.length() >= 256) s = s.substring(0, 256);
            ((CPacketChatMessage) event.getPacket()).message = s;
        }
    });

    private boolean isCommand(String s) {
        for (String value : CustomChat.cmdCheck) {
            if (s.startsWith(value)) return true;
        }
        return false;
    }

    private String leetSwitch(String i) {
        switch (i) {
            case "a":
                return "4";
            case "e":
                return "3";
            case "g":
                return "6";
            case "l":
            case "i":
                return "1";
            case "o":
                return "0";
            case "s":
                return "$";
            case "t":
                return "7";
            default: return i;
        }
    }
}
