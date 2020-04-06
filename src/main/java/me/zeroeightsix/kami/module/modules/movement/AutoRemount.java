package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.EnumHand;

@Module.Info(name = "AutoRemount", description = "Automatically remounts your horse", category = Module.Category.MOVEMENT)
public class AutoRemount extends Module {
    private Setting<Float> range = register(Settings.floatBuilder("Range").withMinimum(1.0f).withValue(1.5f).withMaximum(10.0f).build());

    public void onUpdate() {
        for (Entity e : mc.world.getLoadedEntityList()) {
            if (e instanceof EntityHorse && !(mc.player.isRidingHorse())) {
                final EntityHorse horse = (EntityHorse) e;
                if (mc.player.getDistance(horse) <= range.getValue()) {
                    mc.playerController.interactWithEntity(mc.player, horse, EnumHand.MAIN_HAND);
                }
            }
        }
    }
}
