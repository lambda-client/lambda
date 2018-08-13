package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.module.modules.player.TpsSync;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author 086
 */
@Mixin(ItemBow.class)
public class MixinItemBow {

    @Inject(method = "getMaxItemUseDuration", cancellable = true, at = @At("HEAD"))
    public void getMaxItemUseDuration(ItemStack stack, CallbackInfoReturnable returnable) {
        if (TpsSync.isSync()) returnable.setReturnValue((int) (72000 * (20f/LagCompensator.INSTANCE.getTickRate())));
    }

}
