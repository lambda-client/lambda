package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.init.Items;

@Module.Info(name = "TestForItems", category = Module.Category.GUI, description = "Hides the armour on selected entities", showOnArray = Module.ShowOnArray.ON)
public class TestForItems extends Module {
    public void onUpdate() {
        for (int i = 0; i < 100; i++) {
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.IRON_INGOT) {
                Command.sendChatMessage(i + "");
                break;
            }
        }
    }
}
