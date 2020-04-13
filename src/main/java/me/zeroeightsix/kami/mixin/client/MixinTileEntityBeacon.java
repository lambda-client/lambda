package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.module.modules.render.NoRender;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;

@Mixin(TileEntityBeacon.class)
public class MixinTileEntityBeacon {
    @Inject(method = "shouldBeamRender", at = @At("HEAD"), cancellable = true)
    public void shouldBeamRender(CallbackInfoReturnable<Float> returnable) {
        if (MODULE_MANAGER.isModuleEnabled(NoRender.class) && MODULE_MANAGER.getModuleT(NoRender.class).beacon.getValue()) {
            returnable.setReturnValue(0.0F);
            returnable.cancel();
        }
    }
}
