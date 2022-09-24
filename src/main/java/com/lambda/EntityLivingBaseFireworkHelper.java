package com.lambda;

import com.lambda.client.module.modules.movement.ElytraFlight;
import com.lambda.mixin.accessor.AccessorEntityFireworkRocket;
import com.lambda.mixin.entity.MixinEntityLivingBase;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;

/**
 * Using {@link AccessorEntityFireworkRocket} in {@link MixinEntityLivingBase} causes a crash on older
 * Mixin versions (like the one Impact uses). Putting the methods using AccessorEntityFireworkRocket outside
 * the MixinEntityLivingBase seems to fix the issue.
 */
public class EntityLivingBaseFireworkHelper {
    public static boolean shouldWork(EntityLivingBase entity) {
        return EntityPlayerSP.class.isAssignableFrom(entity.getClass())
            && ElytraFlight.INSTANCE.isEnabled()
            && ElytraFlight.INSTANCE.getMode().getValue() == ElytraFlight.ElytraFlightMode.VANILLA;
    }

    public static boolean shouldModify(EntityLivingBase entity) {
        return shouldWork(entity) && entity.world.loadedEntityList.stream().anyMatch(firework -> {
                if (firework instanceof AccessorEntityFireworkRocket) {
                    EntityLivingBase boosted = ((AccessorEntityFireworkRocket) firework).getBoostedEntity();
                    return boosted != null && boosted.equals(entity);
                }

                return false;
            }
        );
    }

}
