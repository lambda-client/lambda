package com.lambda.client.mixin.client.render;

import com.lambda.client.module.modules.render.EnchantColor;
import net.minecraft.client.renderer.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderItem.class)
public class MixinRenderItem {

    @ModifyArg(method = "renderEffect", at = @At(value = "INVOKE", target = "net/minecraft/client/renderer/RenderItem.renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;I)V"), index = 1)
    private int renderEffect(int oldValue) {
        if (EnchantColor.INSTANCE.isEnabled()) {
            if(EnchantColor.INSTANCE.getRainbow()) return EnchantColor.INSTANCE.getRainbowValue();
            return EnchantColor.INSTANCE.getNormalValue();
        } return oldValue;
    }
}