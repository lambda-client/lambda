package me.zeroeightsix.kami.event.events;

import me.zeroeightsix.kami.event.KamiEvent;
import net.minecraft.entity.Entity;

public class EntityUseTotem extends KamiEvent {
    private Entity entity;

    public EntityUseTotem(Entity entity) {
        super();
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}