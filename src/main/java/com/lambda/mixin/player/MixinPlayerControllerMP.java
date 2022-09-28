package com.lambda.mixin.player;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.PlayerAttackEvent;
import com.lambda.client.event.events.WindowClickEvent;
import com.lambda.client.module.modules.player.AutoEat;
import com.lambda.client.module.modules.player.TpsSync;
import com.lambda.client.util.TpsCalculator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Redirect(method = "onPlayerDamageBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getPlayerRelativeBlockHardness(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)F"))
    float getPlayerRelativeBlockHardness(@NotNull IBlockState state, EntityPlayer player, World worldIn, BlockPos pos) {
        return state.getPlayerRelativeBlockHardness(player, worldIn, pos) * (TpsSync.INSTANCE.isEnabled() ? (TpsCalculator.INSTANCE.getTickRate() / 20f) : 1);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    public void attackEntity(EntityPlayer playerIn, Entity targetEntity, CallbackInfo ci) {
        if (targetEntity == null) return;
        PlayerAttackEvent event = new PlayerAttackEvent(targetEntity);
        LambdaEventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "windowClick", at = @At("HEAD"), cancellable = true)
    public void onWindowClick(int windowId, int slotId, int mouseButton, ClickType type, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        WindowClickEvent event = new WindowClickEvent(windowId, slotId, mouseButton, type);
        LambdaEventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            cir.cancel();
        }
    }

    @Inject(method = "onStoppedUsingItem", at = @At("HEAD"), cancellable = true)
    public void onStoppedUsingItemMixin(EntityPlayer player, CallbackInfo ci) {
        if (AutoEat.INSTANCE.getEating()) {
            ci.cancel();
        }
    }
}
