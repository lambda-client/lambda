package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.ISetting;
import me.zeroeightsix.kami.util.EntityUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Chams", category = Module.Category.RENDER, description = "See entities through walls")
public class Chams extends Module {

    @ISetting(name = "Players") private static boolean players = true;
    @ISetting(name = "Animals") private static boolean animals = false;
    @ISetting(name = "Mobs") private static boolean mobs = false;

    public static boolean renderChams(Entity entity) {
        return (entity instanceof EntityPlayer ? players : (EntityUtil.isPassive(entity) ? animals : mobs));
    }

}
