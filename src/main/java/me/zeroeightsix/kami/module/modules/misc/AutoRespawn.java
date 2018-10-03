package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.GuiScreenEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.client.gui.GuiGameOver;

/**
 * Created by 086 on 9/04/2018.
 */
@Module.Info(name = "AutoRespawn", description = "Automatically respawns upon death and tells you where you died", category = Module.Category.MISC)
public class AutoRespawn extends Module {

    @Setting(name = "DeathCoords") private boolean deathCoords = false;
    @Setting(name = "Respawn") private boolean respawn = true;

    @EventHandler
    public Listener<GuiScreenEvent.Displayed> listener = new Listener<>(event -> {
        if (event.getScreen() instanceof GuiGameOver) {
            if (deathCoords)
                Command.sendChatMessage(String.format("You died at x %d y %d z %d", (int)mc.player.posX, (int)mc.player.posY, (int)mc.player.posZ));

            if (respawn) {
                mc.player.respawnPlayer();
                mc.displayGuiScreen(null);
            }
        }
    });

}
