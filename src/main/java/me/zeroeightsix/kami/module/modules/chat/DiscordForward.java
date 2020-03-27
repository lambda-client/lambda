package me.zeroeightsix.kami.module.modules.chat;

import com.mrpowergamerbr.temmiewebhook.DiscordMessage;
import com.mrpowergamerbr.temmiewebhook.TemmieWebhook;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.gui.InfoOverlay;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.ColourTextFormatting;
import me.zeroeightsix.kami.util.TimeUtil;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.text.TextFormatting;

import java.util.regex.Pattern;

import static me.zeroeightsix.kami.util.ColourTextFormatting.toTextMap;

/**
 * @author S-B99
 * Created by S-B99 on 26/03/20
 */
@Module.Info(name = "DiscordForward", category = Module.Category.CHAT, description = "Sends your chat to a set Discord channel")
public class DiscordForward extends Module {
    private Setting<Boolean> time = register(Settings.b("Timestamp", true));
    private Setting<Boolean> all = register(Settings.b("All Messages", false));
    private Setting<Boolean> queue = register(Settings.booleanBuilder("Queue Position").withValue(true).withVisibility(v -> !all.getValue()).build());
    private Setting<Boolean> direct = register(Settings.booleanBuilder("Direct Messages").withValue(true).withVisibility(v -> !all.getValue()).build());
    public Setting<String> url = register(Settings.s("URL", "unchanged"));
    public Setting<String> avatar = register(Settings.s("Avatar", KamiMod.GITHUB_LINK + "raw/assets/assets/icons/kami.png"));

    @EventHandler
    public Listener<PacketEvent.Receive> listener = new Listener<>(event -> {
        if (mc.player == null || isDisabled()) return;
        if (!(event.getPacket() instanceof SPacketChat)) return;

        SPacketChat sPacketChat = (SPacketChat) event.getPacket();
        if (shouldSend(sPacketChat.getChatComponent().getUnformattedText())) {
            sendMessage(KamiMod.MODNAME, getTime() + sPacketChat.chatComponent.getUnformattedText(), avatar.getValue());
        }
    });

    public void onEnable() {
        if (url.getValue().equalsIgnoreCase("unchanged")) {
            Command.sendErrorMessage(getChatName() + "You must first set a webhook url with the '&7" + Command.getCommandPrefix() + "discordforward&r' command");
            disable();
        }
    }

    private boolean shouldSend(String message) {
        if (all.getValue()) return true;
        else if (isQueue(message)) return true;
        else return isDirect(message);
    }

    private boolean isDirect(String message) {
        if (direct.getValue() && message.contains("whispers:")) return true;
        else return direct.getValue() && Pattern.compile("to.*:", Pattern.CASE_INSENSITIVE).matcher(message).find();
    }

    private boolean isQueue(String message) {
        if (queue.getValue() && message.contains("Position in queue:")) {
            return true;
        } else return queue.getValue() && message.contains("2b2t is full");
    }

    private String getTime() {
        if (!time.getValue() || ModuleManager.getModuleByName("ChatTimestamp").isEnabled()) return "";
        InfoOverlay info = (InfoOverlay) ModuleManager.getModuleByName("InfoOverlay");
        return "[" + TimeUtil.getFinalTime(info.timeUnitSetting.getValue(), info.timeTypeSetting.getValue(), info.doLocale.getValue()) + "] ";
    }

    private void sendMessage(String username, String content, String avatarUrl) {
        TemmieWebhook tm = new TemmieWebhook(url.getValue());
        DiscordMessage dm = new DiscordMessage(username, content, avatarUrl);
        tm.sendMessage(dm);
    }
}
