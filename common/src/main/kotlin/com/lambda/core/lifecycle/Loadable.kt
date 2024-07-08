package com.lambda.core.lifecycle

import com.lambda.core.lifecycle.Lifecycle.Complete

internal annotation class ForgeLikeOnly

/**
 * Represents the lifecycle of the mod loader process for Forge and NeoForge mods.
 * Managers ran on Fabric with one of these lifecycles will be ignored and executed on the [Complete]
 * lifecycle instead.
 */
@ForgeLikeOnly
sealed class Lifecycle {
    /**
     * Fired when the mod events are registered. Not to confuse with Lambda's event bus.
     */
    interface Initialization : Loadable

    /**
     * Fired right after the mod constructor is called
     */
    interface Construction : Loadable

    /**
     * Fired for each individual Minecraft registry.
     * As long as you are still in the lifecycle scope you are allowed
     * to add entries without any issues.
     */
    interface Registry : Loadable

    /**
     * Fired during the early initialisation of Minecraft.
     */
    interface Common : Loadable

    /**
     * Sided-setup on their respective side, however only `FMLClientSetupEvent` will be fired
     * because Lambda is not a server-sided mod.
     */
    interface SideSetup : Loadable

    /**
     * Fired when mods can send messages to each other for cross-mod compatibility.
     * This is the last event before the mod is considered fully loaded.
     */
    interface InterModCommunication : Loadable

    /**
     * Fired when the mod is fully loaded and ready to be used.
     */
    interface Complete : Loadable
}

/**
 * Represents a loadable object.
 */
interface Loadable {
    fun load() = this::class.simpleName?.let { "Loaded $it" } ?: "Loaded"
}
