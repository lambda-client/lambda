package me.zeroeightsix.kami.module.modules.bewwawho.combat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.ClientPlayerAttackEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.zeroeightysix.Friends;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;

/***
 * @author Sasha
 */
@Module.Info(name = "AntiFriendHit", description = "Don't hit your friends", category = Module.Category.COMBAT, alwaysListening = true)
public class AntiFriendHit extends Module {


    @EventHandler
    Listener<ClientPlayerAttackEvent> listener = new Listener<>(event -> {
        if (!this.isEnabled()) return;
        Entity e = mc.objectMouseOver.entityHit;
        if (e instanceof EntityOtherPlayerMP && Friends.isFriend(e.getName())) {
            event.cancel();
        }
    });


}
