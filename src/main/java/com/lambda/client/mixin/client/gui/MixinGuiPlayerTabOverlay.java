package com.lambda.client.mixin.client.gui;

import com.lambda.client.module.modules.render.ExtraTab;
import com.lambda.client.module.modules.render.TabFriends;
import kotlin.collections.CollectionsKt;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {

    private List<NetworkPlayerInfo> preSubList = CollectionsKt.emptyList();

    @ModifyVariable(method = "renderPlayerlist", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    public List<NetworkPlayerInfo> renderPlayerlistStorePlayerListPre(List<NetworkPlayerInfo> list) {
        preSubList = list;
        return list;
    }

    @ModifyVariable(method = "renderPlayerlist", at = @At(value = "STORE", ordinal = 1), ordinal = 0)
    public List<NetworkPlayerInfo> renderPlayerlistStorePlayerListPost(List<NetworkPlayerInfo> list) {
        return ExtraTab.subList(preSubList, list);
    }

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    public void getPlayerName(NetworkPlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        if (TabFriends.INSTANCE.isEnabled()) {
            cir.setReturnValue(TabFriends.getPlayerName(networkPlayerInfoIn));
        }
    }

}
