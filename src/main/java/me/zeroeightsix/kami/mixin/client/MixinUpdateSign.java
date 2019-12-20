package me.zeroeightsix.kami.mixin.client;

import net.minecraft.network.play.client.CPacketUpdateSign;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/***
 * @author S-B99
 */
@Mixin(CPacketUpdateSign.class)
public class MixinUpdateSign {

//    @Inject(method = "<init>()V", at = @At(value = "INVOKE", target = ""))
//    public void onInit(CallbackInfo info) {
//
//    }
}
