package com.lambda.mixin.player;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.CriticalsUpdateWalkingEvent;
import com.lambda.client.event.events.OnUpdateWalkingPlayerEvent;
import com.lambda.client.event.events.PlayerMoveEvent;
import com.lambda.client.event.events.PushOutOfBlocksEvent;
import com.lambda.client.gui.mc.LambdaGuiBeacon;
import com.lambda.client.manager.managers.MessageManager;
import com.lambda.client.manager.managers.PlayerPacketManager;
import com.lambda.client.module.modules.chat.PortalChat;
import com.lambda.client.module.modules.misc.BeaconSelector;
import com.lambda.client.module.modules.movement.Sprint;
import com.lambda.client.module.modules.player.Freecam;
import com.lambda.client.util.Wrapper;
import com.lambda.client.util.math.Vec2f;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = EntityPlayerSP.class, priority = Integer.MAX_VALUE)
public abstract class MixinEntityPlayerSP extends EntityPlayer {
    @Shadow @Final public NetHandlerPlayClient connection;
    @Shadow public MovementInput movementInput;
    @Shadow public float renderArmYaw;
    @Shadow public float renderArmPitch;
    @Shadow public float prevRenderArmYaw;
    @Shadow public float prevRenderArmPitch;
    @Shadow protected Minecraft mc;
    @Shadow private double lastReportedPosX;
    @Shadow private double lastReportedPosY;
    @Shadow private double lastReportedPosZ;
    @Shadow private float lastReportedYaw;
    @Shadow private int positionUpdateTicks;
    @Shadow private float lastReportedPitch;
    @Shadow private boolean serverSprintState;
    @Shadow private boolean serverSneakState;
    @Shadow private boolean prevOnGround;
    @Shadow private boolean autoJumpEnabled;

    public MixinEntityPlayerSP(World worldIn, GameProfile gameProfileIn) {
        super(worldIn, gameProfileIn);
    }

    @Shadow
    protected abstract void updateAutoJump(float p_189810_1_, float p_189810_2_);

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;closeScreen()V"))
    public void closeScreen(EntityPlayerSP player) {
        if (PortalChat.INSTANCE.isDisabled()) player.closeScreen();
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    public void closeScreen(Minecraft minecraft, GuiScreen screen) {
        if (PortalChat.INSTANCE.isDisabled()) Wrapper.getMinecraft().displayGuiScreen(screen);
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        PushOutOfBlocksEvent event = new PushOutOfBlocksEvent();
        LambdaEventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            callbackInfoReturnable.setReturnValue(false);
        }
    }

    /**
     * @author TBM
     * Used with full permission from TBM - l1ving
     */
    @Inject(method = "displayGUIChest", at = @At("HEAD"), cancellable = true)
    public void onDisplayGUIChest(IInventory chestInventory, CallbackInfo ci) {
        if (BeaconSelector.INSTANCE.isEnabled()) {
            if (chestInventory instanceof IInteractionObject && "minecraft:beacon".equals(((IInteractionObject) chestInventory).getGuiID())) {
                Minecraft.getMinecraft().displayGuiScreen(new LambdaGuiBeacon(inventory, chestInventory));
                ci.cancel();
            }
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    public void moveHead(MoverType type, double x, double y, double z, CallbackInfo ci) {
        EntityPlayerSP player = Wrapper.getPlayer();
        if (player == null) return;

        PlayerMoveEvent event = new PlayerMoveEvent(player);
        LambdaEventBus.INSTANCE.post(event);

        if (event.isModified()) {
            double prevX = posX;
            double prevZ = posZ;

            super.move(type, event.getX(), event.getY(), event.getZ());
            updateAutoJump((float) (posX - prevX), (float) (posZ - prevZ));

            ci.cancel();
        }
    }

    @ModifyArg(method = "setSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;setSprinting(Z)V"), index = 0)
    public boolean modifySprinting(boolean sprinting) {
        if (Sprint.INSTANCE.isEnabled() && Sprint.shouldSprint()) {
            return true;
        } else {
            return sprinting;
        }
    }

    // Cannot use an inject in isCurrentViewEntity due to rusherhack redirecting it here
    @Inject(method = "onUpdateWalkingPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isCurrentViewEntity()Z"), cancellable = true)
    protected void mixinUpdateWalkingPlayerCompat(CallbackInfo ci) {
        if (Freecam.INSTANCE.isEnabled() && Freecam.INSTANCE.getCameraGuy() != null && Objects.equals(this, mc.player)) {
            ci.cancel();
            // we need to perform the same actions as what is in the mc method
            ++positionUpdateTicks;
            final AxisAlignedBB boundingBox = getEntityBoundingBox();
            final Vec3d pos = new Vec3d(posX, boundingBox.minY, posZ);
            final Vec2f rot = new Vec2f(rotationYaw, rotationPitch);
            final boolean isMoving = isMoving(pos);
            final boolean isRotating = isRotating(rot);
            sendPlayerPacket(isMoving, isRotating, pos, rot);
            if (isMoving) {
                lastReportedPosX = pos.x;
                lastReportedPosY = pos.y;
                lastReportedPosZ = pos.z;
                positionUpdateTicks = 0;
            }

            if (isRotating) {
                lastReportedYaw = rot.getX();
                lastReportedPitch = rot.getY();
            }

            prevOnGround = onGround;
            autoJumpEnabled = mc.gameSettings.autoJump;
        }
    }

    // Cannot use an inject in isCurrentViewEntity due to rusherhack redirecting it here
    @Inject(method = "updateEntityActionState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isCurrentViewEntity()Z"), cancellable = true)
    protected void mixinEntityActionState(CallbackInfo ci) {
        if (Freecam.INSTANCE.isEnabled() && Freecam.INSTANCE.getCameraGuy() != null && Objects.equals(this, mc.player)) {
            ci.cancel();

            // we need to perform the same actions as what is in the mc method
            moveStrafing = movementInput.moveStrafe;
            moveForward = movementInput.moveForward;
            isJumping = movementInput.jump;
            prevRenderArmYaw = renderArmYaw;
            prevRenderArmPitch = renderArmPitch;
            renderArmPitch = renderArmPitch + (rotationPitch - renderArmPitch) * 0.5f;
            renderArmYaw = renderArmYaw + (rotationYaw - renderArmYaw) * 0.5f;
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"))
    public void sendChatMessage(String message, CallbackInfo ci) {
        MessageManager.INSTANCE.setLastPlayerMessage(message);
    }

    @Inject(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;onUpdateWalkingPlayer()V", shift = At.Shift.AFTER))
    private void onUpdateInvokeOnUpdateWalkingPlayer(CallbackInfo ci) {
        Vec3d serverSidePos = PlayerPacketManager.INSTANCE.getServerSidePosition();
        Vec2f serverSideRotation = PlayerPacketManager.INSTANCE.getPrevServerSideRotation();

        lastReportedPosX = serverSidePos.x;
        lastReportedPosY = serverSidePos.y;
        lastReportedPosZ = serverSidePos.z;

        lastReportedYaw = serverSideRotation.getX();
        lastReportedPitch = serverSideRotation.getY();
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateWalkingPlayerHead(CallbackInfo ci) {
        if (Freecam.INSTANCE.isEnabled() && Freecam.INSTANCE.getCameraGuy() != null
            && Objects.equals(this, Freecam.INSTANCE.getCameraGuy())) return;

        CriticalsUpdateWalkingEvent criticalsEditEvent = new CriticalsUpdateWalkingEvent();
        LambdaEventBus.INSTANCE.post(criticalsEditEvent);

        // Setup flags
        Vec3d position = new Vec3d(posX, getEntityBoundingBox().minY, posZ);
        Vec2f rotation = new Vec2f(rotationYaw, rotationPitch);
        boolean moving = isMoving(position);
        boolean rotating = isRotating(rotation);

        OnUpdateWalkingPlayerEvent event = new OnUpdateWalkingPlayerEvent(moving, rotating, position, rotation);
        LambdaEventBus.INSTANCE.post(event);

        event = event.nextPhase();
        LambdaEventBus.INSTANCE.post(event);

        if (event.getCancelled()) {
            ci.cancel();

            if (!event.getCancelAll()) {
                // Copy flags from event
                moving = event.isMoving();
                rotating = event.isRotating();
                position = event.getPosition();
                rotation = event.getRotation();

                sendSprintPacket();
                sendSneakPacket();
                sendPlayerPacket(moving, rotating, position, rotation);

                prevOnGround = onGround;
            }

            ++positionUpdateTicks;
            autoJumpEnabled = mc.gameSettings.autoJump;
        }

        event = event.nextPhase();
        LambdaEventBus.INSTANCE.post(event);
    }

    private void sendSprintPacket() {
        boolean sprinting = isSprinting();

        if (sprinting != serverSprintState) {
            if (sprinting) {
                connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_SPRINTING));
            } else {
                connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.STOP_SPRINTING));
            }
            serverSprintState = sprinting;
        }
    }

    private void sendSneakPacket() {
        boolean sneaking = isSneaking();

        if (sneaking != serverSneakState) {
            if (sneaking) {
                connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_SNEAKING));
            } else {
                connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.STOP_SNEAKING));
            }
            serverSneakState = sneaking;
        }
    }

    private void sendPlayerPacket(boolean moving, boolean rotating, Vec3d position, Vec2f rotation) {
        if (isRiding()) {
            connection.sendPacket(new CPacketPlayer.PositionRotation(motionX, -999.0D, motionZ, rotation.getX(), rotation.getY(), onGround));
            moving = false;
        } else if (moving && rotating) {
            connection.sendPacket(new CPacketPlayer.PositionRotation(position.x, position.y, position.z, rotation.getX(), rotation.getY(), onGround));
        } else if (moving) {
            connection.sendPacket(new CPacketPlayer.Position(position.x, position.y, position.z, onGround));
        } else if (rotating) {
            connection.sendPacket(new CPacketPlayer.Rotation(rotation.getX(), rotation.getY(), onGround));
        } else if (prevOnGround != onGround) {
            connection.sendPacket(new CPacketPlayer(onGround));
        }

        if (moving) positionUpdateTicks = 0;
    }

    private boolean isMoving(Vec3d position) {
        double xDiff = position.x - lastReportedPosX;
        double yDiff = position.y - lastReportedPosY;
        double zDiff = position.z - lastReportedPosZ;

        return positionUpdateTicks >= 20 || xDiff * xDiff + yDiff * yDiff + zDiff * zDiff > 9.0E-4D;
    }

    private boolean isRotating(Vec2f rotation) {
        double yawDiff = rotation.getX() - lastReportedYaw;
        double pitchDiff = rotation.getY() - lastReportedPitch;
        return yawDiff != 0.0D || pitchDiff != 0.0D;
    }
}
