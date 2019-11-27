package me.zeroeightsix.kami.module.modules.sdashb.combat;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.gui.GuiGameOver;

@Module.Info(name = "AntiDeathScreen", description = "Fixs random death screen glitches", category = Module.Category.COMBAT)
public class AntiDeathScreen extends Module {

    //private Setting<Boolean> respawn = this.register(Settings.b("Respawn", true));

    @Override
    public void onUpdate()
    {
        if (!this.isEnabled()) return;
        if (mc.player.getHealth() > 0 && mc.currentScreen instanceof GuiGameOver)
        {
            mc.player.respawnPlayer();
            mc.displayGuiScreen(null);
        }
    }

}
