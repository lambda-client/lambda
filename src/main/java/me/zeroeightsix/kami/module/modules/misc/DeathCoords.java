package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.GuiScreenEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.client.gui.GuiGameOver;

/**
 * Created by 086 on 9/04/2018.
 */
@Module.Info(name = "DeathCoords", description = "Tells you where you die upon death", category = Module.Category.MISC)
public class DeathCoords extends Module {

    @EventHandler
    public Listener<GuiScreenEvent.Displayed> listener = new Listener<>(event -> {
        if (event.getScreen() instanceof GuiGameOver)
            Command.sendChatMessage(String.format("You died at x %d y %d z %d", (int)mc.player.posX, (int)mc.player.posY, (int)mc.player.posZ));
    });

}
