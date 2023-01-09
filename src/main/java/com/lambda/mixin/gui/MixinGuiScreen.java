package com.lambda.mixin.gui;

import com.lambda.client.manager.managers.AltManager;
import com.lambda.client.module.modules.render.ContainerPreview;
import com.lambda.client.module.modules.render.MapPreview;
import com.lambda.client.module.modules.render.NoRender;
import com.lambda.client.util.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.storage.MapData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {
    @Shadow
    protected List<GuiButton> buttonList;

    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true)
    public void renderToolTip(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (MapPreview.INSTANCE.isEnabled() && stack.getItem() instanceof ItemMap) {
            MapData mapData = MapPreview.getMapData(stack);

            if (mapData != null) {
                ci.cancel();
                MapPreview.drawMap(stack, mapData, x, y);
            }
        } else if (ContainerPreview.INSTANCE.isEnabled()) {
            ContainerPreview.INSTANCE.renderTooltips(stack, x, y, ci);
        }
    }

    @Inject(method = "drawWorldBackground(I)V", at = @At("HEAD"), cancellable = true)
    private void drawWorldBackgroundWrapper(final int tint, final CallbackInfo ci) {
        if (Wrapper.getWorld() != null && NoRender.INSTANCE.isEnabled() && (NoRender.INSTANCE.getInventoryGlobal())) {
            ci.cancel();
        }
    }
    
    @Inject(method = "setWorldAndResolution", at = @At("TAIL"))
    private void setWorldAndResolution(Minecraft mc, int width, int height, CallbackInfo ci) {
        if (GuiMultiplayer.class.isAssignableFrom(getClass())) {
            buttonList.add(new GuiButton(AltManager.BUTTON_ID, width - 32, height - 20, 32, 20, "Alts"));
        }
    }
}
