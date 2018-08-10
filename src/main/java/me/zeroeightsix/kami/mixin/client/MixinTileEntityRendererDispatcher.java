package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.RenderTileEntityEvent;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author 086
 */
@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    public void render(TileEntity tileEntityIn, double x, double y, double z, float partialTicks, int destroyStage, float p_192854_10_, CallbackInfo info) {
        RenderTileEntityEvent entityEvent = new RenderTileEntityEvent(tileEntityIn);
        KamiMod.EVENT_BUS.post(entityEvent);
        if (entityEvent.isCancelled()) info.cancel();
    }

}
