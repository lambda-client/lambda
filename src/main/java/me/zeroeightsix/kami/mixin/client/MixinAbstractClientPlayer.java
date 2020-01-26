package me.zeroeightsix.kami.mixin.client;

import com.mojang.authlib.GameProfile;
import me.zeroeightsix.kami.module.modules.capes.LayerCape;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//i love crystallinqq <3

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer {

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    public void AbstractClientPlayer(World worldIn, GameProfile playerProfile, CallbackInfo callbackInfo) {
        for (RenderPlayer renderPlayer : Wrapper.getMinecraft().getRenderManager().getSkinMap().values()) {
            //if (ModuleManager.getModuleByName("Cape").isEnabled()) {
                LayerCape cape = new LayerCape(renderPlayer);
                renderPlayer.addLayer(cape);
            //}
        }
    }
}