package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.module.modules.render.ExtraTab;
import me.zeroeightsix.kami.module.modules.render.TabFriends;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by 086 on 8/04/2018.
 */
@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {

    private AtomicReference<List> list1 = null;

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE", target = "Ljava/util/List;subList(II)Ljava/util/List;", remap = false))
    public List subList(List list, int fromIndex, int toIndex) {
        if (list1 == null) {
            list1 = new AtomicReference<>(list.subList(fromIndex, Math.min(1, list.size())));
        }

        new Thread(() -> {
            list1.set(list.subList(fromIndex, ExtraTab.INSTANCE.isEnabled() ? Math.min(ExtraTab.INSTANCE.tabSize.getValue(), list.size()) : toIndex));
        }).start();
        
        return list1.get();
    }

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    public void getPlayerName(NetworkPlayerInfo networkPlayerInfoIn, CallbackInfoReturnable returnable) {
        if (TabFriends.INSTANCE.isEnabled()) {
            returnable.cancel();
            returnable.setReturnValue(TabFriends.getPlayerName(networkPlayerInfoIn));
        }
    }

}
