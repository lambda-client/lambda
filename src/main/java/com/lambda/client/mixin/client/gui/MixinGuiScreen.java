package com.lambda.client.mixin.client.gui;

import com.lambda.client.module.modules.render.CleanGUI;
import com.lambda.client.module.modules.render.MapPreview;
import com.lambda.client.module.modules.render.ContainerPreview;
import com.lambda.client.util.Wrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.storage.MapData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true)
    public void renderToolTip(ItemStack stack, int x, int y, CallbackInfo info) {
        if (ContainerPreview.INSTANCE.isEnabled() && (stack.getItem() instanceof ItemShulkerBox || Block.getBlockFromItem(stack.getItem()) instanceof BlockEnderChest)) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

            if (stack.getItem() instanceof ItemShulkerBox) {
                NBTTagCompound tagCompound = ContainerPreview.INSTANCE.getShulkerData(stack);
                if (tagCompound != null) {
                    ItemStackHelper.loadAllItems(tagCompound, items);
                } else {
                    items = null;
                }
            } else {
                // Item is an Ender Chest
                IInventory inventory = ContainerPreview.INSTANCE.getEnderChestItems();
                if (inventory != null) {
                    for (int i = 0; i < items.size(); i++) {
                        items.set(i, inventory.getStackInSlot(i));
                    }
                }
            }

            if (items != null) {
                info.cancel();
                ContainerPreview.INSTANCE.renderContainerAndItems(stack, x, y, items);
            }
        } else if (MapPreview.INSTANCE.isEnabled() && stack.getItem() instanceof ItemMap) {
            MapData mapData = MapPreview.getMapData(stack);

            if (mapData != null) {
                info.cancel();
                MapPreview.drawMap(stack, mapData, x, y);
            }
        }
    }

    @Inject(method = "drawWorldBackground(I)V", at = @At("HEAD"), cancellable = true)
    private void drawWorldBackgroundWrapper(final int tint, final CallbackInfo ci) {
        if (Wrapper.getWorld() != null && CleanGUI.INSTANCE.isEnabled() && (CleanGUI.INSTANCE.getInventoryGlobal().getValue())) {
            ci.cancel();
        }
    }
}
