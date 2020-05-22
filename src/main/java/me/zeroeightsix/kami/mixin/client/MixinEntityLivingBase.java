package me.zeroeightsix.kami.mixin.client;

import me.zero.alpine.type.EventState;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.TravelEvent;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Nucleus
 *
 * The (Object) this looks super weird, but it works because at runtime this is casted to EntityPlayerSP, as it's inherited
 */
@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBase extends MixinEntity {
    public MixinEntityLivingBase() { super(); }

    @Inject(method = "travel", at = @At("HEAD"))
    public void preTravel(CallbackInfo ci) {
        if (Wrapper.getPlayer() == (Object) this) {
            KamiMod.EVENT_BUS.post(new TravelEvent(EventState.PRE));
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    public void postTravel(CallbackInfo ci) {
        if (Wrapper.getPlayer() == (Object) this) {
            KamiMod.EVENT_BUS.post(new TravelEvent(EventState.POST));
        }
    }
}
