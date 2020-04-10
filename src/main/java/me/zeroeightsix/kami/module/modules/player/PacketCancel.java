package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.*;

/**
 * @author S-B99
 */
@Module.Info(name = "PacketCancel", description = "Cancels specific packets used for various actions", category = Module.Category.PLAYER)
public class PacketCancel extends Module {
    private Setting<Boolean> all = register(Settings.b("All", false));
    private Setting<Boolean> packetInput = register(Settings.booleanBuilder("CPacketInput").withValue(true).withVisibility(v -> !all.getValue()));
    private Setting<Boolean> packetPlayer = register(Settings.booleanBuilder("CPacketPlayer").withValue(true).withVisibility(v -> !all.getValue()));
    private Setting<Boolean> packetEntityAction = register(Settings.booleanBuilder("CPacketEntityAction").withValue(true).withVisibility(v -> !all.getValue()));
    private Setting<Boolean> packetUseEntity = register(Settings.booleanBuilder("CPacketUseEntity").withValue(true).withVisibility(v -> !all.getValue()));
    private Setting<Boolean> packetVehicleMove = register(Settings.booleanBuilder("CPacketVehicleMove").withValue(true).withVisibility(v -> !all.getValue()));

    @EventHandler
    private Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
        if (all.getValue() && event.getPacket() != null) event.cancel();
        if (packetInput.getValue() && event.getPacket() instanceof CPacketInput) event.cancel();
        if (packetPlayer.getValue() && event.getPacket() instanceof CPacketPlayer) event.cancel();
        if (packetEntityAction.getValue() && event.getPacket() instanceof CPacketEntityAction) event.cancel();
        if (packetUseEntity.getValue() && event.getPacket() instanceof CPacketUseEntity) event.cancel();
        if (packetVehicleMove.getValue() && event.getPacket() instanceof CPacketVehicleMove) event.cancel();
    });
}
