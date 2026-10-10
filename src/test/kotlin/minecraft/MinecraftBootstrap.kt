package minecraft

import net.minecraft.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.resource.LifecycledResourceManagerImpl
import net.minecraft.resource.ResourceType
import net.minecraft.resource.VanillaDataPackProvider

/**
 * The game's registries with the vanilla data pack's tags bound, as a server has them after
 * loading its data packs: enough to turn block states into the physics the planner captures.
 * The console streams the bootstrap routes into its logger are put back, so test output keeps
 * its format.
 */
object MinecraftBootstrap {
	private val bootstrapped: Unit by lazy {
		val out = System.out
		val err = System.err
		SharedConstants.createGameVersion()
		Bootstrap.initialize()
		System.setOut(out)
		System.setErr(err)
		LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, listOf(VanillaDataPackProvider.createDefaultPack())).use { resources ->
			TagGroupLoader.startReload(resources, DynamicRegistryManager.of(Registries.REGISTRIES)).forEach { it.apply() }
		}
	}

	fun ensure() = bootstrapped
}
