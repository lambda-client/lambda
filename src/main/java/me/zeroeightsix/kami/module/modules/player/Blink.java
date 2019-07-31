package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.event.events.PlayerMoveEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketInput;
import net.minecraft.network.play.client.CPacketPlayer;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by 086 on 24/01/2018.
 * Edited by Cuhnt on 30/7/2019
 */
@Module.Info(name = "Blink", category = Module.Category.PLAYER)
public class Blink extends Module {

    private double posX, posY, posZ;
    private float pitch, yaw;

    private EntityOtherPlayerMP clonedPlayer;

    private boolean isRidingEntity;
    private Entity ridingEntity;


    Queue<CPacketPlayer> packets = new LinkedList<>();

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (isEnabled() && event.getPacket() instanceof CPacketPlayer) {
            event.cancel();
            packets.add((CPacketPlayer) event.getPacket());
        }
    });


    @Override
    protected void onEnable() {
        if (mc.player != null) {
            isRidingEntity = mc.player.getRidingEntity() != null;


            pitch = mc.player.rotationPitch;
            yaw = mc.player.rotationYaw;

            clonedPlayer = new EntityOtherPlayerMP(mc.world, mc.getSession().getProfile());
            clonedPlayer.copyLocationAndAnglesFrom(mc.player);
            clonedPlayer.rotationYawHead = mc.player.rotationYawHead;
            mc.world.addEntityToWorld(-100, clonedPlayer);

        }
    }

    @Override
    protected void onDisable() {
        while (!packets.isEmpty())
            mc.player.connection.sendPacket(packets.poll());

        EntityPlayer localPlayer = mc.player;
        if (localPlayer != null) {
            mc.world.removeEntityFromWorld(-100);
            clonedPlayer = null;
            posX = posY = posZ = 0.D;
            pitch = yaw = 0.f;
        }
    }





    @Override
    public String getHudInfo() {
        return String.valueOf(packets.size());
    }
}
