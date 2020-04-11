package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.modules.client.InfoOverlay;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;

/**
 * Created by Dewy on the 4th of April, 2020
 */
@Module.Info(name = "ElytraReplace", description = "Automatically replace your Elytra when it breaks. Not an AFK tool, be warned.", category = Module.Category.MOVEMENT)
public class ElytraReplace extends Module {
    private Setting<InventoryMode> inventoryMode = register(Settings.e("Inventoryable", InventoryMode.ON));

    private boolean currentlyMovingElytra = false;
    private int elytraCount;

    private enum InventoryMode { ON, OFF }

    @Override
    public void onUpdate() {

        if (inventoryMode.getValue().equals(InventoryMode.OFF) && mc.currentScreen instanceof GuiContainer) {
            return;
        }

        elytraCount = InfoOverlay.getItems(Items.ELYTRA) + InfoOverlay.getArmor(Items.ELYTRA);

        if (currentlyMovingElytra) {
            mc.playerController.windowClick(0, 6, 0, ClickType.PICKUP, mc.player);
            currentlyMovingElytra = false;

            return;
        }

        if (!(mc.player.inventory.armorInventory.get(2).getItem() == Items.ELYTRA)) {

            if (elytraCount == 0) {
                return;
            }

            int slot = -420;

            for (int i = 0; i < 45; i++) {
                if (mc.player.inventory.getStackInSlot(i).getItem() == Items.ELYTRA) {
                    slot = i;

                    break;
                }
            }

            mc.playerController.windowClick(0, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
            currentlyMovingElytra = true;
        }
    }

    @Override
    public String getHudInfo() {
        return Integer.toString(elytraCount);
    }
}
