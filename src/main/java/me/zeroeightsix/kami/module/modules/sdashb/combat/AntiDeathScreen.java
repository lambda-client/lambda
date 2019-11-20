package me.zeroeightsix.kami.module.modules.sdashb.combat;

import java.util.function.Predicate;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;

@Module.Info(name = "AntiDeathScreen", description = "Fixs random death screen glitches", category = Module.Category.EXPERIMENTAL)
public class AntiDeathScreen extends Module {
    private Setting<Boolean> respawn = this.register(Settings.b("Respawn", true));
    @EventHandler
    public Listener listener;

    public AntiDeathScreen() {
        listener = new Listener((event) -> {
    //        if (event.getScreen() instanceof GuiGameOver && (Boolean)this.respawn.getValue()) {
    //            mc.player.respawnPlayer();
    //            if (!mc.player.isDead) {
    //                mc.displayGuiScreen((GuiScreen)null);
    //            }
    //        }

        }, new Predicate[0]);
    }
}
