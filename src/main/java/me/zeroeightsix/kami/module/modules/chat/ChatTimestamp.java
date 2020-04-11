package me.zeroeightsix.kami.module.modules.chat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.ColourTextFormatting;
import me.zeroeightsix.kami.util.TimeUtil;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.text.TextFormatting;

import static me.zeroeightsix.kami.util.ColourTextFormatting.toTextMap;

/**
 * @author S-B99
 * Updated by S-B99 on 25/03/20
 */
@Module.Info(name = "ChatTimestamp", category = Module.Category.CHAT, description = "Shows the time a message was sent beside the message", showOnArray = Module.ShowOnArray.OFF)
public class ChatTimestamp extends Module {
    private Setting<ColourTextFormatting.ColourCode> firstColour = register(Settings.e("First Colour", ColourTextFormatting.ColourCode.GRAY));
    private Setting<ColourTextFormatting.ColourCode> secondColour = register(Settings.e("Second Colour", ColourTextFormatting.ColourCode.WHITE));
    private Setting<TimeUtil.TimeType> timeTypeSetting = register(Settings.e("Time Format", TimeUtil.TimeType.HHMM));
    private Setting<TimeUtil.TimeUnit> timeUnitSetting = register(Settings.e("Time Unit", TimeUtil.TimeUnit.H12));
    private Setting<Boolean> doLocale = register(Settings.b("Show AMPM", true));

    @EventHandler
    public Listener<PacketEvent.Receive> listener = new Listener<>(event -> {
        if (mc.player == null || isDisabled()) return;

        if (!(event.getPacket() instanceof SPacketChat)) return;
        SPacketChat sPacketChat = (SPacketChat) event.getPacket();

        if (addTime(sPacketChat.getChatComponent().getFormattedText())) {
            event.cancel();
        }
    });

    private boolean addTime(String message) {
        Command.sendRawChatMessage("<" + TimeUtil.getFinalTime(setToText(secondColour.getValue()), setToText(firstColour.getValue()), timeUnitSetting.getValue(), timeTypeSetting.getValue(), doLocale.getValue()) + TextFormatting.RESET + "> " + message);
        return true;
    }

    public String returnFormatted() {
        return "<" + TimeUtil.getFinalTime(timeUnitSetting.getValue(), timeTypeSetting.getValue(), doLocale.getValue()) + "> ";
    }

    private TextFormatting setToText(ColourTextFormatting.ColourCode colourCode) {
        return toTextMap.get(colourCode);
    }
}