package me.zeroeightsix.kami.mixin.client;

import com.mojang.authlib.GameProfile;
import me.zeroeightsix.kami.module.modules.capes.Capes;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//i love crystallinqq <3

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer {

    @Inject(method = "getLocationCape", at = @At(value = "RETURN"))
    public void getCape(CallbackInfoReturnable<ResourceLocation> callbackInfo) {
        if (Capes.INSTANCE == null)
            return;
        if ((!Capes.INSTANCE.overrideOtherCapes.getValue()) && (callbackInfo.getReturnValue() != null))
            return;
        // This is weird with a capital W.
        // Essentially, the "mixin class" content is actually aliased over to the actual target class.
        // But that's a runtime thing, so the Java Compiler doesn't know anything about this.
        ResourceLocation kamiCape = Capes.getCapeResource((AbstractClientPlayer) (Object) this);
        if (kamiCape != null)
            callbackInfo.setReturnValue(kamiCape);
    }
}
