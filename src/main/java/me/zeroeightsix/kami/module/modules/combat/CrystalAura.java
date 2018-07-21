package me.zeroeightsix.kami.module.modules.combat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.modules.render.Tracers;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.util.EntityUtil;
import me.zeroeightsix.kami.util.Friends;
import me.zeroeightsix.kami.util.GeometryMasks;
import me.zeroeightsix.kami.util.KamiTessellator;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Explosion;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static me.zeroeightsix.kami.util.EntityUtil.calculateLookAt;

/**
 * Created by 086 on 28/12/2017.
 */
@Module.Info(name = "CrystalAura", category = Module.Category.COMBAT)
public class CrystalAura extends Module {

    @Setting(name = "Range") private double range = 4;
    @Setting(name = "Place") private boolean place = false;
    @Setting(name = "Players") private boolean players = true;
    @Setting(name = "Mobs") private boolean mobs = false;
    @Setting(name = "Animals") private boolean animals = false;

    private BlockPos render;
    private Entity renderEnt;
    private boolean fix = false;

    @Override
    public void onUpdate() {
        EntityEnderCrystal crystal = mc.world.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityEnderCrystal)
                .map(entity -> (EntityEnderCrystal) entity)
                .min(Comparator.comparing(c -> mc.player.getDistance(c)))
                .orElse(null);
        if (crystal != null && mc.player.getDistance(crystal) <= range) {
            lookAtPacket(crystal.posX, crystal.posY, crystal.posZ, mc.player);
            mc.player.connection.sendPacket(new CPacketUseEntity(crystal));
            mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
            return;
        }

        int crystalSlot = mc.player.getHeldItemMainhand().getItem() == Items.END_CRYSTAL ? mc.player.inventory.currentItem : -1;
        if (crystalSlot == -1)
            for (int l = 0; l < 9; ++l) {
                if (mc.player.inventory.getStackInSlot(l).getItem() == Items.END_CRYSTAL) {
                    crystalSlot = l;
                    break;
                }
            }
        boolean offhand = false;
        if (mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL) offhand = true;
        else if (crystalSlot == -1) return;

        List<BlockPos> blocks = findCrystalBlocks();
        List<Entity> entities = new ArrayList<>();
        if (players) entities.addAll(mc.world.playerEntities.stream().filter(entityPlayer -> !Friends.isFriend(entityPlayer.getName())).collect(Collectors.toList()));
        entities.addAll(mc.world.loadedEntityList.stream().filter(entity -> EntityUtil.isLiving(entity) && (EntityUtil.isPassive(entity) ? animals : mobs)).collect(Collectors.toList()));

        BlockPos q = null;
        double damage = .5;
        for (Entity entity : entities) {
            if (entity == mc.player || ((EntityLivingBase)entity).getHealth() <= 0) continue;
            for (BlockPos blockPos : blocks) {
                double b = entity.getDistanceSq(blockPos);
                if (b >= 169) continue; // If this block if further than 13 (3.6^2, less calc) blocks, ignore it. It'll take no or very little damage
                double d = calculateDamage(blockPos.x+.5,blockPos.y+1,blockPos.z+.5, entity);
                if (d > damage) {
                    double self = calculateDamage(blockPos.x+.5,blockPos.y+1,blockPos.z+.5, mc.player);
                    // If this deals more damage to ourselves than it does to our target, continue. This is only ignored if the crystal is sure to kill our target but not us.
                    // Also continue if our crystal is going to hurt us.. alot
                    if ((self > d && !(d < ((EntityLivingBase) entity).getHealth())) || self-.5 > mc.player.getHealth()) continue;
                    damage = d;
                    q = blockPos;
                    renderEnt = entity;
                }
            }
        }
        if (damage == .5) {
            render = null;
            renderEnt = null;
            active = false;
            if (fix) {
                mc.player.connection.sendPacket(new CPacketPlayer.PositionRotation(mc.player.posX, mc.player.posY, mc.player.posZ, mc.player.rotationYaw, mc.player.rotationPitch, mc.player.onGround));
                fix = false;
            }
            return;
        }
        render = q;

        if (place) {
            if (!offhand && mc.player.inventory.currentItem != crystalSlot) {
                mc.player.inventory.currentItem = crystalSlot;
                mc.player.connection.sendPacket(new CPacketHeldItemChange(crystalSlot));
                return;
            }
            lookAtPacket(q.x+.5,q.y-.5,q.z+.5, mc.player);
            active = true;
            fix = true;
            RayTraceResult result = mc.world.rayTraceBlocks(new Vec3d(mc.player.posX, mc.player.posY+mc.player.getEyeHeight(), mc.player.posZ), new Vec3d(q.x+.5,q.y-.5d,q.z+.5));
            EnumFacing f;
            if (result == null || result.sideHit == null) f = EnumFacing.UP;
            else f = result.sideHit;
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(q, f, offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND, 0, 0, 0));
        }
    }

    @Override
    public void onWorldRender(RenderEvent event) {
        if (render != null) {
            KamiTessellator.prepare(GL11.GL_QUADS);
            KamiTessellator.drawBox(render, 0x44ffffff, GeometryMasks.Quad.ALL);
            KamiTessellator.release();
            if (renderEnt != null) {
                Vec3d p = EntityUtil.getInterpolatedRenderPos(renderEnt, mc.getRenderPartialTicks());
                Tracers.drawLineFromPosToPos(render.x-mc.getRenderManager().renderPosX+.5d,render.y-mc.getRenderManager().renderPosY+1,render.z-mc.getRenderManager().renderPosZ+.5d,p.x,p.y,p.z,renderEnt.getEyeHeight(),1,1,1,1);
            }
        }
    }

    private void lookAtPacket(double px, double py, double pz, EntityPlayer me)
    {
        double[] v = calculateLookAt(px, py, pz, me);
        float pY = yaw;
        float pP = pitch;
        if ((float)v[0] != pY || (float)v[1] != pP || !active) {
            yaw = (float)v[0];
            pitch = (float)v[1];
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation((float)v[0], (float)v[1], mc.player.onGround));
        }
    }

    private boolean canPlaceCrystal(BlockPos blockPos) {
        Block m = mc.world.getBlockState(blockPos).getBlock();
        if (m != Blocks.BEDROCK && m != Blocks.OBSIDIAN) return false;
        BlockPos boost = blockPos.add(0,1,0);
        m = mc.world.getBlockState(boost).getBlock();
        if (m != Blocks.AIR) return false;
        if (!mc.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(boost)).isEmpty()) return false;
        return true;
    }

    public static BlockPos getPlayerPos(){
        return new BlockPos(Math.floor(mc.player.posX),Math.floor(mc.player.posY),Math.floor(mc.player.posZ));
    }

    private List<BlockPos> findCrystalBlocks() {
        NonNullList<BlockPos> positions = NonNullList.create();
        positions.addAll(getSphere(getPlayerPos(), (float)range, (int) range, false, true, 0).stream().filter(this::canPlaceCrystal).collect(Collectors.toList()));
        return positions;
    }

    public List<BlockPos> getSphere(BlockPos loc, float r, int h, boolean hollow, boolean sphere, int plus_y) {
        List<BlockPos> circleblocks = new ArrayList<>();
        int cx = loc.getX();
        int cy = loc.getY();
        int cz = loc.getZ();
        for (int x = cx - (int)r; x <= cx + r; x++) {
            for (int z = cz - (int)r; z <= cz + r; z++) {
                for (int y = (sphere ? cy - (int)r : cy); y < (sphere ? cy + r : cy + h); y++) {
                    double dist = (cx - x) * (cx - x) + (cz - z) * (cz - z) + (sphere ? (cy - y) * (cy - y) : 0);
                    if (dist < r * r && !(hollow && dist < (r - 1) * (r - 1))) {
                        BlockPos l = new BlockPos(x, y + plus_y, z);
                        circleblocks.add(l);
                    }
                }
            }
        }
        return circleblocks;
    }

    public static float calculateDamage(double posX, double posY, double posZ, Entity entity){
        float doubleExplosionSize = 6.0F * 2.0F;
        double distancedsize = entity.getDistance(posX, posY, posZ) / (double)doubleExplosionSize;
        Vec3d vec3d = new Vec3d(posX, posY, posZ);
        double blockDensity = (double)entity.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
        double v = (1.0D - distancedsize) * blockDensity;
        float damage = (float)((int)((v * v + v) / 2.0D * 7.0D * (double)doubleExplosionSize + 1.0D));
        double finald = 1;
        /*if (entity instanceof EntityLivingBase)
            finald = getBlastReduction((EntityLivingBase) entity,getDamageMultiplied(damage));*/
        if (entity instanceof EntityLivingBase){
            finald = getBlastReduction((EntityLivingBase) entity,getDamageMultiplied(damage), new Explosion(mc.world, null, posX, posY, posZ, 6F, false, true));
        }
        return (float)finald;
    }

    public static float getBlastReduction(EntityLivingBase entity, float damage, Explosion explosion){
        if (entity instanceof EntityPlayer){
            EntityPlayer ep = (EntityPlayer) entity;
            DamageSource ds = DamageSource.causeExplosionDamage(explosion);
            damage = CombatRules.getDamageAfterAbsorb(damage, (float)ep.getTotalArmorValue(), (float)ep.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());

            int k = EnchantmentHelper.getEnchantmentModifierDamage(ep.getArmorInventoryList(), ds);
            float f = MathHelper.clamp(k, 0.0F, 20.0F);
            damage = damage * (1.0F - f / 25.0F);

            if (entity.isPotionActive(Potion.getPotionById(11))){
                damage = damage - (damage/4);
            }

            damage = Math.max(damage - ep.getAbsorptionAmount(), 0.0F);
            return damage;
        }
        damage = CombatRules.getDamageAfterAbsorb(damage, (float)entity.getTotalArmorValue(), (float)entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        return damage;
    }

    public static float getDamageMultiplied(float damage){
        int diff = mc.world.getDifficulty().getDifficultyId();
        return damage*(diff==0 ? 0 : (diff == 2 ? 1 : (diff == 1 ? 0.5f : 1.5f)));
    }

    public static float calculateDamage(EntityEnderCrystal crystal, Entity entity){
        return calculateDamage(crystal.posX, crystal.posY, crystal.posZ, entity);
    }

    private float yaw;
    private float pitch;
    private boolean active = false;

    @EventHandler
    private Listener<PacketEvent.Send> packetListener = new Listener<>(event -> {
        if (!active) return;
        if (event.getPacket() instanceof CPacketPlayer.PositionRotation) {
            ((CPacketPlayer.PositionRotation) event.getPacket()).yaw = yaw;
            ((CPacketPlayer.PositionRotation) event.getPacket()).pitch = pitch;
        }else if (event.getPacket() instanceof CPacketPlayer.Rotation) {
            ((CPacketPlayer.Rotation) event.getPacket()).yaw = yaw;
            ((CPacketPlayer.Rotation) event.getPacket()).pitch = pitch;
        }
    });
}
