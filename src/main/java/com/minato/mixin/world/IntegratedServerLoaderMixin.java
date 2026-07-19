
package com.minato.mixin.world;

import com.minato.module.modules.combat.autodisconnect.AutoDisconnect;
import com.minato.module.modules.combat.autodisconnect.SingleplayerReconnectTarget;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServerLoader.class)
public class IntegratedServerLoaderMixin {
    @Inject(method = "start(Ljava/lang/String;Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void onStart(String name, Runnable onCancel, CallbackInfo ci) {
        AutoDisconnect.INSTANCE.setLastReconnectTarget(new SingleplayerReconnectTarget(name));
    }

}
