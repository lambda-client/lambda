package com.lambda.mixin.network;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.SafeClientEvent;
import com.lambda.client.manager.managers.CrystalManager;
import com.lambda.client.util.combat.CombatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraftforge.event.world.ExplosionEvent;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    @Shadow private Minecraft client;

    @Shadow private WorldClient world;

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void onExplosionPacket(@NotNull SPacketExplosion packetIn, CallbackInfo ci) {
        Vec3d position = new Vec3d(packetIn.getX(), packetIn.getY(), packetIn.getZ());
        // TODO: Get not only crystals
        Entity exploder = this.world.getLoadedEntityList().stream().filter(e -> e.getPosition().equals(position))
            .findFirst()
            .orElse(null);
        Explosion explosion;
        if (exploder != null) explosion = new Explosion(this.client.world, exploder, packetIn.getX(), packetIn.getY(), packetIn.getZ(), packetIn.getStrength(), packetIn.getAffectedBlockPositions());
        else explosion = new Explosion(this.client.world, (Entity)null, packetIn.getX(), packetIn.getY(), packetIn.getZ(), packetIn.getStrength(), packetIn.getAffectedBlockPositions());
        LambdaEventBus.INSTANCE.post(new ExplosionEvent.Start(this.world, explosion));
        explosion.doExplosionB(true);

        float f3 = packetIn.getStrength() * 2.0F;
        int k1 = MathHelper.floor(packetIn.getX() - (double)f3 - 1.0D);
        int l1 = MathHelper.floor(packetIn.getX() + (double)f3 + 1.0D);
        int i2 = MathHelper.floor(packetIn.getY() - (double)f3 - 1.0D);
        int i1 = MathHelper.floor(packetIn.getY() + (double)f3 + 1.0D);
        int j2 = MathHelper.floor(packetIn.getMotionZ() - (double)f3 - 1.0D);
        int j1 = MathHelper.floor(packetIn.getZ() + (double)f3 + 1.0D);
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(explosion.getExplosivePlacedBy(), new AxisAlignedBB(k1, i2, j2, l1, i1, j1));
        LambdaEventBus.INSTANCE.post(new ExplosionEvent.Detonate(this.world, explosion, list));
        this.client.player.motionX += packetIn.getMotionX();
        this.client.player.motionY += packetIn.getMotionY();
        this.client.player.motionZ += packetIn.getMotionZ();
        ci.cancel();
    }
}
