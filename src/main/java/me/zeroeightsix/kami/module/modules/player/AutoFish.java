package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketSoundEffect;

/**
 * Created by 086 on 22/03/2018.
 */
@Module.Info(name = "AutoFish", category = Module.Category.MISC, description = "Automatically catch fish")
public class AutoFish extends Module {

    @EventHandler
    private Listener<PacketEvent.Receive> receiveListener = new Listener<>(event -> {
        if (mc.player != null && (mc.player.getHeldItemMainhand().getItem() == Items.FISHING_ROD || mc.player.getHeldItemOffhand().getItem() == Items.FISHING_ROD) && event.getPacket() instanceof SPacketSoundEffect && SoundEvents.ENTITY_BOBBER_SPLASH.equals(((SPacketSoundEffect) event.getPacket()).getSound())) {
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mc.rightClickMouse();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mc.rightClickMouse();
            }).start();
        }
    });

}
