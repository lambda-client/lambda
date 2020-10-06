package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.module.modules.render.ESP;
import me.zeroeightsix.kami.module.modules.render.Nametags;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
abstract class MixinRender<T extends Entity> {
    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true)
    protected void renderNamePre(T entityIn, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (Nametags.INSTANCE.isEnabled() && Nametags.INSTANCE.checkEntityType(entityIn)) {
            ci.cancel();
        } else if (ESP.INSTANCE.isEnabled() && ESP.INSTANCE.getDrawingOutline()) {
            if (ESP.INSTANCE.getDrawNametag()) {
                Wrapper.getMinecraft().getFramebuffer().bindFramebuffer(false);
            } else {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderLivingLabel", at = @At("RETURN"))
    protected void renderNamePost(T entityIn, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (ESP.INSTANCE.isEnabled() && ESP.INSTANCE.getDrawingOutline()) {
            if (ESP.INSTANCE.getFrameBuffer() != null) ESP.INSTANCE.getFrameBuffer().bindFramebuffer(false);
        }
    }
}
