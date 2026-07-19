
package com.minato.mixin.render;

import com.minato.module.modules.render.ContainerPreview;
import com.minato.module.modules.render.MapPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.MapRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Shadow
    @Final
    MinecraftClient client;
    @Unique boolean adjustSize = false;
    @Shadow
    @Final
    public GuiRenderState state;

    @Unique
    private final MapRenderState mapRenderState = new MapRenderState();

    @Shadow
    public abstract Matrix3x2fStack getMatrices();

    @Shadow
    public abstract void drawMap(MapRenderState mapRenderState);

    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "TAIL"))
    private void injectDrawMap(TextRenderer textRenderer, ItemStack stack, int i, int j, String string, CallbackInfo ci) {
        if (MapPreview.INSTANCE.isDisabled() || !MapPreview.getShowInSlot()) return;

        if (!stack.isOf(Items.FILLED_MAP)) return;

        var mapId = stack.get(DataComponentTypes.MAP_ID);
        var savedData = FilledMapItem.getMapState(mapId, client.world);

        if (savedData == null) return;

        this.getMatrices().pushMatrix();
        this.getMatrices().translate(i, j);
        this.getMatrices().scale(0.125F, 0.125F);

        client.getMapRenderer().update(mapId, savedData, this.mapRenderState);
        this.drawMap(this.mapRenderState);

        this.getMatrices().popMatrix();
    }

    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true)
    private void onDrawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y, @Nullable Identifier texture, CallbackInfo ci) {
        if (!ContainerPreview.INSTANCE.isEnabled()) return;
        if (ContainerPreview.isRenderingSubTooltip()) return;

        if (ContainerPreview.isLocked()) {
            ci.cancel();
            ContainerPreview.renderLockedTooltip((DrawContext)(Object)this, textRenderer);
            return;
        }

        if (data.isPresent() && data.get() instanceof ContainerPreview.ContainerComponent) {
            ci.cancel();
            ContainerPreview.renderShulkerTooltip((DrawContext)(Object)this, textRenderer, x, y);
        }
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addItem(Lnet/minecraft/client/gui/render/state/ItemGuiElementRenderState;)V", shift = At.Shift.AFTER))
    private void onDrawItem(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (!ContainerPreview.INSTANCE.isEnabled()) return;
        ContainerPreview.drawOnItem((DrawContext) (Object) this, state, entity, world, stack, x, y, seed);
    }
}
