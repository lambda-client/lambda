package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.PlayerMoveEvent;
import me.zeroeightsix.kami.module.modules.chat.PortalChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.MoverType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by 086 on 12/12/2017.
 */
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    @SuppressWarnings("UnnecessaryReturnStatement")
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;closeScreen()V"))
    public void closeScreen(EntityPlayerSP entityPlayerSP) {
        if (KamiMod.MODULE_MANAGER.isModuleEnabled(PortalChat.class)) return;
    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    public void closeScreen(Minecraft minecraft, GuiScreen screen) {
        if (KamiMod.MODULE_MANAGER.isModuleEnabled(PortalChat.class)) return;
    }

//    @ModifyArgs(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;move(Lnet/minecraft/entity/MoverType;DDD)V"))
//    public void move(Args args) {
//        MoverType type = args.get(0);
//        double x = args.get(1);<
//        double y = args.get(2);
//        double z = args.get(3);
//        PlayerMoveEvent event = new PlayerMoveEvent(type, x, y, z);
//        KamiMod.EVENT_BUS.post(event);
//        if (event.isCancelled()) {
//            x = y = z = 0;
//        } else {
//            x = event.getX();
//            y = event.getY();
//            z = event.getZ();
//        }
//        args.set(1, x);
//        args.set(2, y);
//        args.set(3, z);
//    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    public void move(MoverType type, double x, double y, double z, CallbackInfo info) {
        PlayerMoveEvent event = new PlayerMoveEvent(type, x, y, z);
        KamiMod.EVENT_BUS.post(event);
        if (event.isCancelled()) info.cancel();
    }

}
