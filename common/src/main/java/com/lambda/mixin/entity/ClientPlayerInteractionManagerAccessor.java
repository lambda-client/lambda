package com.lambda.mixin.entity;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author Doogie13
 * @since 05/06/2024
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {

    @Accessor
    void setLastSelectedSlot(int slot);

    @Accessor
    void setSelectedStack(ItemStack stack);

}
