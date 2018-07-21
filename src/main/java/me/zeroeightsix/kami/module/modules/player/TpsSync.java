package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Created by 086 on 9/04/2018.
 */
@Module.Info(name = "TpsSync", description = "Synchronizes some actions with the server TPS", category = Module.Category.PLAYER)
public class TpsSync extends Module {

    @EventHandler
    public Listener<LivingEntityUseItemEvent.Start> startListener = new Listener<>(event -> event.setDuration((int)(event.getDuration() * (20f/LagCompensator.INSTANCE.getTickRate()))));

}
