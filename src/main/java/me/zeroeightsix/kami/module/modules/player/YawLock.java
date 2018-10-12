package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.ISetting;
import net.minecraft.util.math.MathHelper;

/**
 * Created by 086 on 16/12/2017.
 */
@Module.Info(name = "YawLock", category = Module.Category.PLAYER)
public class YawLock extends Module {
    @ISetting(name = "Auto") boolean auto = true;
    @ISetting(name = "Yaw") float yaw = 180;
    @ISetting(name = "Slice") int slice = 8;

    @Override
    public void onUpdate() {
        if (slice == 0) return;
        if (auto) {
            int angle = 360/slice;
            float yaw = mc.player.rotationYaw;
            yaw = Math.round(yaw/angle)*angle;
            mc.player.rotationYaw = yaw;
            if (mc.player.isRiding()) mc.player.getRidingEntity().rotationYaw = yaw;
        }else{
            mc.player.rotationYaw = MathHelper.clamp(yaw-180, -180, 180);
        }
    }
}
