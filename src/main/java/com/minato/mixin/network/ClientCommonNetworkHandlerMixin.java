
package com.minato.mixin.network;

import com.minato.module.modules.combat.autodisconnect.AutoDisconnect;
import com.minato.module.modules.combat.autodisconnect.AutoDisconnectScreen;
import com.minato.module.modules.combat.autodisconnect.DisconnectDetails;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {
    @Inject(method = "createDisconnectedScreen", at = @At("HEAD"), cancellable = true)
    private void createDisconnectedScreen(DisconnectionInfo info, CallbackInfoReturnable<Screen> cir) {
        DisconnectDetails details = AutoDisconnect.INSTANCE.consumeDetails();
        if (details != null) {
            cir.setReturnValue(new AutoDisconnectScreen(details));
        }
    }
}
