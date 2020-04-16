package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;

// poop aimbot gonna finish tomorrow
@Module.Info(name = "AimBot", description = "Automatically aims at entities for you.", category = Module.Category.COMBAT)
public class AimBot extends Module {

    private Setting<Integer> fov = register(Settings.integerBuilder("FOV").withMinimum(90).withMaximum(360).withValue(360));
    private Setting<Integer> range = register(Settings.integerBuilder("Range").withMinimum(4).withMaximum(64).withValue(48));
    private Setting<Boolean> ignoreWalls = register(Settings.booleanBuilder("Ignore Walls").withValue(true));
    private Setting<Boolean> targetInvis = register(Settings.booleanBuilder("Target Invisible").withValue(true));
    private Setting<Boolean> targetPlayers = register(Settings.booleanBuilder("Target Players").withValue(true));
    private Setting<Boolean> targetFriends = register(Settings.booleanBuilder("Friends").withValue(false).withVisibility(v -> targetPlayers.getValue().equals(true)));
    private Setting<Boolean> targetSleeping = register(Settings.booleanBuilder("Sleeping").withValue(false).withVisibility(v -> targetPlayers.getValue().equals(true)));
    private Setting<Boolean> targetMobs = register(Settings.booleanBuilder("Target Mobs").withValue(false));
    private Setting<Boolean> targetHostileMobs = register(Settings.booleanBuilder("Hostile").withValue(true).withVisibility(v -> targetMobs.getValue().equals(true)));
    private Setting<Boolean> targetPassiveMobs = register(Settings.booleanBuilder("Passive").withValue(false).withVisibility(v -> targetMobs.getValue().equals(true)));

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

    private double normalizeAngle(double angleIn) {
        while (angleIn <= -180.0) {
            angleIn += 360.0;
        }

        while (angleIn > 180.0) {
            angleIn -= 360.0;
        }

        return angleIn;
    }
}
