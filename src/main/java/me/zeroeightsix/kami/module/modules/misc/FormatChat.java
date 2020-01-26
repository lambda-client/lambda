package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;

/**
 * Created on 16 December by 0x2E | PretendingToCode
 */

@Module.Info(name = "FormatChat", description = "Add colour and linebreak support to upstream chat packets", category = Module.Category.MISC)
public class FormatChat extends Module {

    @Override
    public void onEnable() {
        if (Minecraft.getMinecraft().getCurrentServerData() == null) {
            Command.sendWarningMessage("[FormatChat] &6&lWarning: &r&6This does not work in singleplayer");
            this.disable();
        }
        else {
            Command.sendWarningMessage("[FormatChat] &6&lWarning: &r&6This will kick you on most servers!");
        }
    }

    @EventHandler
    public Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String message = ((CPacketChatMessage) event.getPacket()).message;
            if(message.contains("&") || message.contains("#n")){
                message = message.replaceAll("&", KamiMod.colour + "");
                message = message.replaceAll("#n", "\n");

                Wrapper.getPlayer().connection.sendPacket(new CPacketChatMessage(message));
                event.cancel();
            }
        }
    });
}
