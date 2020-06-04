package me.zeroeightsix.kami.util;

import com.google.gson.JsonParser;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.commands.MacroCommand;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;

public class EntityUtil {

    public static boolean mobTypeSettings(Entity e,  Boolean mobs, Boolean passive, Boolean neutral, Boolean hostile) {
        return mobs && ((passive && isPassiveMob(e)) || (neutral && isCurrentlyNeutral(e)) || (hostile && isMobAggressive(e)));
    }

    public static boolean isPassiveMob(Entity e) {// TODO: usages of this
        return e instanceof EntityAnimal || e instanceof EntityAgeable || e instanceof EntityTameable || e instanceof EntityAmbientCreature || e instanceof EntitySquid;
    }

    public static boolean isLiving(Entity e) {
        return e instanceof EntityLivingBase;
    }

    public static boolean isFakeLocalPlayer(Entity entity) {
        return entity != null && entity.getEntityId() == -100 && Wrapper.getPlayer() != entity;
    }

    /**
     * Find the entities interpolated amount
     */
    public static Vec3d getInterpolatedAmount(Entity entity, double x, double y, double z) {
        return new Vec3d(
                (entity.posX - entity.lastTickPosX) * x,
                (entity.posY - entity.lastTickPosY) * y,
                (entity.posZ - entity.lastTickPosZ) * z
        );
    }

    public static Vec3d getInterpolatedAmount(Entity entity, Vec3d vec) {
        return getInterpolatedAmount(entity, vec.x, vec.y, vec.z);
    }

    public static Vec3d getInterpolatedAmount(Entity entity, double ticks) {
        return getInterpolatedAmount(entity, ticks, ticks, ticks);
    }

    public static boolean isMobAggressive(Entity entity) {
        if (entity instanceof EntityPigZombie) {
            // arms raised = aggressive, angry = either game or we have set the anger cooldown
            if (((EntityPigZombie) entity).isArmsRaised() || ((EntityPigZombie) entity).isAngry()) {
                return true;
            }
        } else if (entity instanceof EntityWolf) {
            return ((EntityWolf) entity).isAngry() &&
                    !Wrapper.getPlayer().equals(((EntityWolf) entity).getOwner());
        } else if (entity instanceof EntityEnderman) {
            return ((EntityEnderman) entity).isScreaming();
        } else if (entity instanceof EntityIronGolem) {
            return ((EntityIronGolem) entity).getRevengeTarget() == null;
        }
        return isHostileMob(entity);
    }

    /**
     * If the mob is currently neutral but not aggressive
     */
    public static boolean isCurrentlyNeutral(Entity entity) {
        return (isNeutralMob(entity) && !isMobAggressive(entity));
    }

    /**
     * If the mob by default wont attack the player, but will if the player attacks it
     */
    public static boolean isNeutralMob(Entity entity) {
        return entity instanceof EntityPigZombie ||
                entity instanceof EntityWolf ||
                entity instanceof EntityEnderman ||
                entity instanceof EntityIronGolem;
    }

    /**
     * If the mob is friendly
     */
    public static boolean isFriendlyMob(Entity entity) {
        return (entity.isCreatureType(EnumCreatureType.CREATURE, false) && !EntityUtil.isNeutralMob(entity)) ||
                (entity.isCreatureType(EnumCreatureType.AMBIENT, false)) ||
                entity instanceof EntityVillager;
    }

    /**
     * If the mob is hostile
     */
    public static boolean isHostileMob(Entity entity) {
        return (entity.isCreatureType(EnumCreatureType.MONSTER, false) && !EntityUtil.isNeutralMob(entity));
    }

    /**
     * Find the entities interpolated position
     */
    public static Vec3d getInterpolatedPos(Entity entity, float ticks) {
        return new Vec3d(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ).add(getInterpolatedAmount(entity, ticks));
    }

    public static Vec3d getInterpolatedRenderPos(Entity entity, float ticks) {
        return getInterpolatedPos(entity, ticks).subtract(Wrapper.getMinecraft().getRenderManager().renderPosX, Wrapper.getMinecraft().getRenderManager().renderPosY, Wrapper.getMinecraft().getRenderManager().renderPosZ);
    }

    public static boolean isInWater(Entity entity) {
        if (entity == null) return false;

        double y = entity.posY + 0.01;

        for (int x = MathHelper.floor(entity.posX); x < MathHelper.ceil(entity.posX); x++)
            for (int z = MathHelper.floor(entity.posZ); z < MathHelper.ceil(entity.posZ); z++) {
                BlockPos pos = new BlockPos(x, (int) y, z);

                if (Wrapper.getWorld().getBlockState(pos).getBlock() instanceof BlockLiquid) return true;
            }

        return false;
    }

    public static boolean isDrivenByPlayer(Entity entityIn) {
        return Wrapper.getPlayer() != null && entityIn != null && entityIn.equals(Wrapper.getPlayer().getRidingEntity());
    }

    public static boolean isAboveWater(Entity entity) {
        return isAboveWater(entity, false);
    }

    public static boolean isAboveWater(Entity entity, boolean packet) {
        if (entity == null) return false;

        double y = entity.posY - (packet ? 0.03 : (EntityUtil.isPlayer(entity) ? 0.2 : 0.5)); // increasing this seems to flag more in NCP but needs to be increased so the player lands on solid water

        for (int x = MathHelper.floor(entity.posX); x < MathHelper.ceil(entity.posX); x++)
            for (int z = MathHelper.floor(entity.posZ); z < MathHelper.ceil(entity.posZ); z++) {
                BlockPos pos = new BlockPos(x, MathHelper.floor(y), z);

                if (Wrapper.getWorld().getBlockState(pos).getBlock() instanceof BlockLiquid) return true;
            }

        return false;
    }

    public static double[] calculateLookAt(double px, double py, double pz, EntityPlayer me) {
        double dirx = me.posX - px;
        double diry = me.posY - py;
        double dirz = me.posZ - pz;

        double len = Math.sqrt(dirx * dirx + diry * diry + dirz * dirz);

        dirx /= len;
        diry /= len;
        dirz /= len;

        double pitch = Math.asin(diry);
        double yaw = Math.atan2(dirz, dirx);

        // to degree
        pitch = pitch * 180.0d / Math.PI;
        yaw = yaw * 180.0d / Math.PI;

        yaw += 90f;

        return new double[]{yaw, pitch};
    }

    public static boolean isPlayer(Entity entity) {
        return entity instanceof EntityPlayer;
    }

    public static double getRelativeX(float yaw) {
        return (double) (MathHelper.sin(-yaw * 0.017453292F));
    }

    public static double getRelativeZ(float yaw) {
        return (double) (MathHelper.cos(yaw * 0.017453292F));
    }

    /**
     * Gets the MC username tied to a given UUID.
     *
     * @param uuid UUID to get name from.
     * @return The name tied to the UUID.
     */
    public static String getNameFromUUID(String uuid) {
        try {
            KamiMod.log.info("Attempting to get name from UUID: " + uuid);

            String jsonUrl = IOUtils.toString(new URL("https://api.mojang.com/user/profiles/" + uuid.replace("-", "") + "/names"));

            JsonParser parser = new JsonParser();

            return parser.parse(jsonUrl).getAsJsonArray().get(parser.parse(jsonUrl).getAsJsonArray().size() - 1).getAsJsonObject().get("name").toString();
        } catch (IOException ex) {
            KamiMod.log.error(ex.getStackTrace());

            KamiMod.log.error("Failed to get username from UUID due to an exception. Maybe your internet is being the big gay? Somehow?");
        }

        return null;
    }
}
