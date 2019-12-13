package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.EntityUtil;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

//import java.util.concurrent.TimeUnit;

/**
 * Created by 086 on 19/11/2017.
 * Updated by S-B99 on 08/11/2019
 */
@Module.Info(category = Module.Category.PLAYER, description = "Prevents fall damage", name = "NoFall")
public class NoFall extends Module {

    private Setting<FallMode> fallMode = register(Settings.e("Mode", FallMode.PACKET));
    private Setting<Boolean> pickup = register(Settings.b("Pickup", true));
    private Setting<Integer> distance = register(Settings.i("Distance", 3));

    private long last = 0;

    @EventHandler
    public Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
        if ((fallMode.getValue().equals(FallMode.PACKET)) && event.getPacket() instanceof CPacketPlayer) {
            ((CPacketPlayer) event.getPacket()).onGround = true;
        }
    });

    @Override
    public void onUpdate() {
        if ((fallMode.getValue().equals(FallMode.BUCKET)) && mc.player.fallDistance >= distance.getValue() && !EntityUtil.isAboveWater(mc.player) && System.currentTimeMillis() - last > 100) {
            Vec3d posVec = mc.player.getPositionVector();
            RayTraceResult result = mc.world.rayTraceBlocks(posVec, posVec.add(0, -5.33f, 0), true, true, false);
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                EnumHand hand = EnumHand.MAIN_HAND;
                if (mc.player.getHeldItemOffhand().getItem() == Items.WATER_BUCKET) hand = EnumHand.OFF_HAND;
                else if (mc.player.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) {
                    for (int i = 0; i < 9; i++)
                        if (mc.player.inventory.getStackInSlot(i).getItem() == Items.WATER_BUCKET) {
                            mc.player.inventory.currentItem = i;
                            mc.player.rotationPitch = 90;
                            last = System.currentTimeMillis();
                            return;
                        }
                    return;
                }

                mc.player.rotationPitch = 90;
                mc.playerController.processRightClick(mc.player, mc.world, hand);
                Boolean pickup = true;
            }
            System.out.println("KAMI BLUE: Ran this");
            if (pickup.getValue()) {
                System.out.println("KAMI BLUE: Ran this");
                posVec = mc.player.getPositionVector();
                result = mc.world.rayTraceBlocks(posVec, posVec.add(0, -1.5f, 0), false, true, false);
                if (result != null && result.typeOfHit != RayTraceResult.Type.BLOCK) {
                    System.out.println("KAMI BLUE: Ran this");
                    EnumHand hand = EnumHand.MAIN_HAND;
                    if (mc.player.getHeldItemOffhand().getItem() == Items.BUCKET) hand = EnumHand.OFF_HAND;
                    else if (mc.player.getHeldItemMainhand().getItem() != Items.BUCKET) {
                        for (int iN = 0; iN < 9; iN++)
                            if (mc.player.inventory.getStackInSlot(iN).getItem() == Items.BUCKET) {
                                mc.player.inventory.currentItem = iN;
                                mc.player.rotationPitch = 90;
                                last = System.currentTimeMillis();
                                return;
                            }
                        return;
                    }

                    mc.player.rotationPitch = 90;
                    mc.playerController.processRightClick(mc.player, mc.world, hand);
                    Boolean pickup = false;

//				if (mc.player.getHeldItemOffhand().getItem() == Items.BUCKET) hand = EnumHand.OFF_HAND;
//				else if (mc.player.getHeldItemMainhand().getItem() != Items.BUCKET) {
//					for (int i = 0; i < 9; i++)
//					if (mc.player.inventory.getStackInSlot(i).getItem() == Items.BUCKET) {
//							mc.player.inventory.currentItem = i;
//							mc.player.rotationPitch = 90;
//							last = System.currentTimeMillis();
//							return;
//						}
//					return;
//				}
//
//				if (pickup == true) {
//					mc.playerController.processRightClick(mc.player, mc.world, hand);
//					pickup = false;
//				}


//				System.out.println("KAMI Blue: time is " + System.currentTimeMillis());
//				do {
//
//				}
//				while(System.currentTimeMillis() - last > 400);
//				System.out.println("KAMI Blue: time is " + System.currentTimeMillis());
//
//				System.out.println("KAMI Blue: time is now" + System.currentTimeMillis());


                    // this is where i want to run the above 2 lines again after 300 milliseconds

                    // this was tried individually
                    // result: forgot but it was either a crash or lag
                    //TimeUnit.MILLISECONDS.sleep(400);

                    // result: lag thread
                    //long lastNanoTime = System.nanoTime();
                    //long nowTime = System.nanoTime();
                    //while(nowTime/1000000 - lastNanoTime /1000000 < 300 )
                    //{
                    //	nowTime = System.nanoTime();
                    //	System.out.println("KAMI: Tried to pick up bucket");
                    //	mc.player.rotationPitch = 90;
                    //	mc.playerController.processRightClick(mc.player, mc.world, hand);

                    //}

                    // this was tried individually
                    // result: freeze
                    //Thread.sleep(300);

                    // this was tried individually
                    // result: clean exit
                    //wait(300);

                    //mc.player.rotationPitch = 90;
                    //mc.playerController.processRightClick(mc.player, mc.world, hand);
                }
            }
        }
    }

    private enum FallMode {
        BUCKET, PACKET
    }
}
