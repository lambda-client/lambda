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
// The code here is terrible. Not proud of it. TODO: Make this not suck.
@Module.Info(name = "ElytraReplace", description = "Automatically swap and replace your chestplate and elytra. Not an AFK tool, be warned.", category = Module.Category.MOVEMENT)
public class ElytraReplace extends Module {
    private Setting<InventoryMode> inventoryMode = register(Settings.e("Inventoryable", InventoryMode.ON));

    private boolean currentlyMovingElytra = false;
    private boolean currentlyMovingChestplate = false;

    private int elytraCount;

    private enum InventoryMode { ON, OFF }

    @Override
    public void onUpdate() {

        if (inventoryMode.getValue().equals(InventoryMode.OFF) && mc.currentScreen instanceof GuiContainer) {
            return;
        }

        elytraCount = InfoOverlay.getItems(Items.ELYTRA) + InfoOverlay.getArmor(Items.ELYTRA);

        int chestplateCount = InfoOverlay.getItems(Items.DIAMOND_CHESTPLATE) + InfoOverlay.getArmor(Items.DIAMOND_CHESTPLATE);

        if (currentlyMovingElytra) {
            mc.playerController.windowClick(0, 6, 0, ClickType.PICKUP, mc.player);
            currentlyMovingElytra = false;

            return;
        }

        if (currentlyMovingChestplate) {
            mc.playerController.windowClick(0, 6, 0, ClickType.PICKUP, mc.player);
            currentlyMovingChestplate = false;

            return;
        }

        if (onGround()) {
            if (mc.player.inventory.armorInventory.get(2).isEmpty()) {

                if (chestplateCount == 0) {
                    return;
                }

                int slot = -420;

                for (int i = 0; i < 45; i++) {
                    if (mc.player.inventory.getStackInSlot(i).getItem() == Items.DIAMOND_CHESTPLATE) {
                        slot = i;

                        break;
                    }
                }

                mc.playerController.windowClick(0, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
                currentlyMovingElytra = true;

                return;
            }

            if (!(mc.player.inventory.armorInventory.get(2).getItem() == Items.DIAMOND_CHESTPLATE)) {
                if (chestplateCount == 0) {
                    return;
                }

                int slot = -420;

                for (int i = 0; i < 45; i++) {
                    if (mc.player.inventory.getStackInSlot(i).getItem() == Items.DIAMOND_CHESTPLATE) {
                        slot = i;

                        break;
                    }
                }

                mc.playerController.windowClick(0, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, 6, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
            }
        }

        if (!onGround()) {
            if (mc.player.inventory.armorInventory.get(2).isEmpty()) {

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
                mc.playerController.windowClick(0, 6, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
            }
        }
    }

    @Override
    public String getHudInfo() {
        return Integer.toString(elytraCount);
    }

    private boolean onGround() {
        return mc.player.onGround;
    }
}
