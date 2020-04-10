package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author S-B99
 */
@Module.Info(name = "PacketCancel", description = "Cancels specific packets used for various actions", category = Module.Category.PLAYER)
public class PacketCancel extends Module {
    private final Setting<Boolean> all = register(Settings.b("All", false));
    private final Setting<Boolean> packetInput = register(Settings.booleanBuilder("CPacketInput").withValue(true).withVisibility(v -> !all.getValue()));
    private final Setting<Boolean> packetPlayer = register(Settings.booleanBuilder("CPacketPlayer").withValue(true).withVisibility(v -> !all.getValue()));
    private final Setting<Boolean> packetEntityAction = register(Settings.booleanBuilder("CPacketEntityAction").withValue(true).withVisibility(v -> !all.getValue()));
    private final Setting<Boolean> packetUseEntity = register(Settings.booleanBuilder("CPacketUseEntity").withValue(true).withVisibility(v -> !all.getValue()));
    private final Setting<Boolean> packetVehicleMove = register(Settings.booleanBuilder("CPacketVehicleMove").withValue(true).withVisibility(v -> !all.getValue()));

    Queue<Packet> packets = new LinkedList<>();
    @EventHandler
    private final Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
        if (all.getValue() && event.getPacket() != null) event.cancel();
        if (packetInput.getValue() && event.getPacket() instanceof CPacketInput) {
            event.cancel();
            packets.add(event.getPacket());
        }
        if (packetPlayer.getValue() && event.getPacket() instanceof CPacketPlayer) {
            event.cancel();
            packets.add(event.getPacket());
        }
        if (packetEntityAction.getValue() && event.getPacket() instanceof CPacketEntityAction) {
            event.cancel();
            packets.add(event.getPacket());
        }
        if (packetUseEntity.getValue() && event.getPacket() instanceof CPacketUseEntity) {
            event.cancel();
            packets.add(event.getPacket());
        }
        if (packetVehicleMove.getValue() && event.getPacket() instanceof CPacketVehicleMove) {
            event.cancel();
            packets.add(event.getPacket());
        }
    });

    public void onDisable() {
        packets.clear();
    }

    @Override
    public String getHudInfo() {
        return String.valueOf(packets.size());
    }
}
