
package com.minato.mixin.render;

import com.minato.graphics.outline.IEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityRenderState {
    @Unique
    private int lambda$entityId = -1;

    @Override
    public int lambda$getEntityId() {
        return lambda$entityId;
    }

    @Override
    public void lambda$setEntityId(int id) {
        this.lambda$entityId = id;
    }
}
