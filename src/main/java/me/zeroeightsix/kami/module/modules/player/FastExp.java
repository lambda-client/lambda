package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.Module.Info;
import net.minecraft.init.Items;

/**
 * Created by S-B99 on 23/10/2019
 */

@Module.Info(category = Module.Category.PLAYER, description = "Removes delay when holding right click", name = "FastUse")
public class FastExp
extends Module
{
	@EventHandler
	private Listener<PacketEvent.Receive> receiveListener = new Listener<>(event ->
	{

		if (mc.player != null && (mc.player.getHeldItemMainhand().getItem() == Items.EXPERIENCE_BOTTLE || mc.player.getHeldItemOffhand().getItem() == Items.EXPERIENCE_BOTTLE))
		{
			mc.rightClickDelayTimer = 0;
		}
	}
			);
}