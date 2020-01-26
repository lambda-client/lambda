package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.client.Minecraft;

@Module.Info(name = "ShulkerBypass", category = Module.Category.RENDER, description = "Bypasses the shulker preview patch on 2b2t")
public class ShulkerBypass extends Module {
    public void onEnable() {
        if (Minecraft.getMinecraft().player == null) return;
        Command.sendChatMessage("[ShulkerBypass] To use this throw a shulker on the ground");
    }
}
