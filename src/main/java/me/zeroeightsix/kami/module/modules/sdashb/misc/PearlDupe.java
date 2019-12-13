package me.zeroeightsix.kami.module.modules.sdashb.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketConfirmTeleport;

@Module.Info(name = "PearlDupe", description = "Duplicates your inventory", category = Module.Category.MISC)
public class PearlDupe extends Module {

    private Setting<Boolean> info = register(Settings.b("Info", true));
    private Setting<Boolean> warn = register(Settings.b("Warning", true));
    private Setting<Boolean> singleplayer = register(Settings.b("SinglePlayer Disable", true));
    private Setting<Boolean> disable = register(Settings.b("Disable on death", true));

    public void onEnable() {
        if (mc.player == null) return;
        if (Minecraft.getMinecraft().getCurrentServerData() == null) {
            Command.sendErrorMessage("[PearlDupe] Error: &r&4This doesn't work in singleplayer");
            if (singleplayer.getValue()) {
                this.disable();
            }
            return;
        }
        if (info.getValue()) {
            Command.sendChatMessage("[PearlDupe] Instructions: throw a pearl, it /kills on teleport ");
            Command.sendChatMessage("[PearlDupe] This doesn't always work, and it doesn't work for 2b2t and 9b9t");
        }
        if (warn.getValue()) {
            Command.sendWarningMessage("[PearlDupe] Warning is still on, please disable the option once you've read the instructions");
            this.disable();
        }
    }

    @EventHandler
    Listener<PacketEvent.Receive> receiveListener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketConfirmTeleport) {
//            if (mc.player == null) return;
            Minecraft.getMinecraft().playerController.connection.sendPacket(new CPacketChatMessage("/kill"));
            if (disable.getValue()) {
                this.disable();
            }
        }
    });
}
