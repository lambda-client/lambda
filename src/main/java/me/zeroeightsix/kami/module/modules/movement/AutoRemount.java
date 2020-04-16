package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityDonkey;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.EnumHand;

/**
 * @author S-B99
 * Created by S-B99 on 05/04/20
 * Updated by S-B99 on 07/04/20
 */
@Module.Info(name = "AutoRemount", description = "Automatically remounts your horse", category = Module.Category.MOVEMENT)
public class AutoRemount extends Module {
    private Setting<Mode> modeSetting = register(Settings.e("Mode", Mode.HORSE));
    private Setting<Float> range = register(Settings.floatBuilder("Range").withMinimum(1.0f).withValue(1.5f).withMaximum(10.0f).build());

    private enum Mode { HORSE, DONKEY }

    public void onUpdate() {
        switch (modeSetting.getValue()) {
            case HORSE:
                for (Entity e : mc.world.getLoadedEntityList()) {
                    if (e instanceof EntityHorse && !(mc.player.isRidingHorse())) {
                        final EntityHorse horse = (EntityHorse) e;
                        if (mc.player.getDistance(horse) <= range.getValue()) {
                            mc.playerController.interactWithEntity(mc.player, horse, EnumHand.MAIN_HAND);
                        }
                    }
                }
            case DONKEY:
                for (Entity e : mc.world.getLoadedEntityList()) {
                    if (e instanceof EntityDonkey && !(mc.player.isRidingHorse())) {
                        final EntityDonkey donkey = (EntityDonkey) e;
                        if (mc.player.getDistance(donkey) <= range.getValue()) {
                            mc.playerController.interactWithEntity(mc.player, donkey, EnumHand.MAIN_HAND);
                        }
                    }
                }
        }

    }
}
