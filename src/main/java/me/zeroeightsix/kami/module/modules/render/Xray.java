package me.zeroeightsix.kami.module.modules.render;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.type.Cancellable;
import me.zeroeightsix.kami.event.events.RenderBlockModelEvent;
import me.zeroeightsix.kami.event.events.SetOpaqueCubeEvent;
import me.zeroeightsix.kami.event.events.ShouldSideBeRenderedEvent;
import me.zeroeightsix.kami.module.Module;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Xray", description = "See blocks through walls", category = Module.Category.RENDER)
public class Xray extends Module {

    public static Xray INSTANCE;

    @Override
    public void toggle() {
        super.toggle();
        mc.renderGlobal.loadRenderers();
    }

    public Xray() {
        Xray.INSTANCE = this;
    }

    @EventHandler
    public Listener<RenderBlockModelEvent> eventListener = new Listener<>(Cancellable::cancel);

    @EventHandler
    public Listener<ShouldSideBeRenderedEvent> shouldSideBeRenderedEventListener = new Listener<>(event -> {
        event.setDoRender(false);
    });

    @EventHandler
    public Listener<SetOpaqueCubeEvent> setOpaqueCubeEventListener = new Listener<>(Cancellable::cancel);

}
