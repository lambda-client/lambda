package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;

/**
 * Created by Dewy on the 16th of April, 2020
 */
@Module.Info(name = "AimBot", description = "Automatically aims at entities for you.", category = Module.Category.COMBAT)
public class AimBot extends Module {

    private Setting<Integer> fov = register(Settings.integerBuilder("FOV").withMinimum(90).withMaximum(360).withValue(360));

    @Override
    public void onUpdate() {
        if (KamiMod.MODULE_MANAGER.getModuleT(Aura.class).isEnabled()) {
            return;
        }

        for(Entity entity : mc.world.loadedEntityList) {
            if(entity instanceof EntityLivingBase) {
                EntityLivingBase e = (EntityLivingBase) entity;

                if(!(e instanceof EntityPlayerSP) && mc.player.getDistance(e) <= 10 && mc.player.canEntityBeSeen(e) && !e.isDead) {
                    faceEntity(e);
                }
            }
        }
    }

    private void faceEntity(Entity entity) {
        double diffX = entity.posX - mc.player.posX;
        double diffZ = entity.posZ - mc.player.posZ;

        double diffY = mc.player.posY + (double) mc.player.getEyeHeight() - (entity.posY + (double) entity.getEyeHeight());
        double xz = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) normalizeAngle((Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f);
        float pitch = (float) normalizeAngle((-Math.atan2(diffY, xz) * 180.0 / Math.PI));

        mc.player.setPositionAndRotation(mc.player.posX, mc.player.posY, mc.player.posZ, yaw, -pitch);
    }

    private static double normalizeAngle(double angle) {
        while (angle <= -180.0) {
            angle += 360.0;
        }

        while (angle > 180.0) {
            angle -= 360.0;
        }

        return angle;
    }
}
