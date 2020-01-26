package me.zeroeightsix.kami.module.modules.combat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import java.util.function.Predicate;

/***
 * @author S-B99
 */
@Module.Info(name = "Criticals", category = Module.Category.COMBAT, description = "Automatically does critical attacks")
public class Criticals extends Module {
    @EventHandler
    private Listener<AttackEntityEvent> attackEntityEventListener;

    public Criticals() {
        this.attackEntityEventListener = new Listener<AttackEntityEvent>(event -> {
            if (!Criticals.mc.player.isInWater() && !Criticals.mc.player.isInLava()) {
                if (Criticals.mc.player.onGround) {
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY + 0.1625, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY + 4.0E-6, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY + 1.0E-6, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer.Position(Criticals.mc.player.posX, Criticals.mc.player.posY, Criticals.mc.player.posZ, false));
                    Criticals.mc.player.connection.sendPacket((Packet) new CPacketPlayer());
                    Criticals.mc.player.onCriticalHit(event.getTarget());
                }
            }
        }, (Predicate<AttackEntityEvent>[]) new Predicate[0]);
    }
}
