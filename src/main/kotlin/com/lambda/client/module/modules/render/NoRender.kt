package com.lambda.client.module.modules.render

import com.lambda.client.event.Phase
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderEntityEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.event.listener.listener
import net.minecraft.block.BlockSnow
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleFirework
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.effect.EntityLightningBolt
import net.minecraft.entity.item.*
import net.minecraft.entity.monster.EntityMob
import net.minecraft.entity.passive.IAnimals
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.*
import net.minecraft.tileentity.*
import net.minecraft.util.ResourceLocation
import net.minecraft.world.EnumSkyBlock
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.registries.GameData
import org.lwjgl.opengl.GL11.GL_QUADS

object NoRender : Module(
    name = "NoRender",
    category = Category.RENDER,
    description = "Ignore entity spawn packets"
) {

    private val packets by setting("Cancel Packets", true)
    private val page by setting("Page", Page.OTHER)

    // Entities
    private val mobs by setting("Mobs", false, { page == Page.ENTITIES })
    private val animals by setting("Animals", false, { page == Page.ENTITIES })
    private val player = setting("Players", false, { page == Page.ENTITIES })
    private val paint = setting("Paintings", false, { page == Page.ENTITIES })
    private val sign = setting("Signs", false, { page == Page.ENTITIES })
    private val skull = setting("Heads", false, { page == Page.ENTITIES })
    private val armorStand = setting("Armor Stands", false, { page == Page.ENTITIES })
    private val endPortal = setting("End Portals", false, { page == Page.ENTITIES })
    private val banner = setting("Banners", false, { page == Page.ENTITIES })
    private val itemFrame = setting("Item Frames", false, { page == Page.ENTITIES })
    private val xp = setting("XP", false, { page == Page.ENTITIES })
    private val items = setting("Items", false, { page == Page.ENTITIES })
    private val crystal = setting("Crystals", false, { page == Page.ENTITIES })
    private val firework = setting("Firework", false, { page == Page.ENTITIES })

    // Others
    val map by setting("Maps", false, { page == Page.OTHER })
    private val explosion by setting("Explosions", true, { page == Page.OTHER })
    val signText by setting("Sign Text", false, { page == Page.OTHER })
    private val particles = setting("Particles", true, { page == Page.OTHER })
    private val falling = setting("Falling Blocks", true, { page == Page.OTHER })
    val beacon by setting("Beacon Beams", true, { page == Page.OTHER })
    private val allLightingUpdates by setting("All Lighting Updates", true, { page == Page.OTHER })
    private val skylight by setting("SkyLight Updates", true, { page == Page.OTHER && !allLightingUpdates })
    private val enchantingTable = setting("Enchanting Books", true, { page == Page.OTHER })
    private val enchantingTableSnow by setting("Enchanting Table Snow", false, { page == Page.OTHER }, description = "Replace enchanting table models with snow layers")
    private val projectiles by setting("Projectiles", false, { page == Page.OTHER })
    private val lightning = setting("Lightning", true, { page == Page.OTHER })

    private enum class Page {
        ENTITIES, OTHER
    }

    private val lambdaMap = ResourceLocation("lambda/lambda_map.png")

    private val settingMap = mapOf(
        player to EntityOtherPlayerMP::class.java,
        paint to EntityPainting::class.java,
        sign to TileEntitySign::class.java,
        skull to TileEntitySkull::class.java,
        armorStand to EntityArmorStand::class.java,
        endPortal to TileEntityEndPortal::class.java,
        banner to TileEntityBanner::class.java,
        itemFrame to EntityItemFrame::class.java,
        xp to EntityXPOrb::class.java,
        items to EntityItem::class.java,
        crystal to EntityEnderCrystal::class.java,
        firework to EntityFireworkRocket::class.java,
        falling to EntityFallingBlock::class.java,
        enchantingTable to TileEntityEnchantmentTable::class.java,
        lightning to EntityLightningBolt::class.java
    )

    var entityList = HashSet<Class<out Any>>(); private set

    init {
        onEnable {
            updateList()
        }

        listener<PacketEvent.Receive> {
            if (explosion && it.packet is SPacketExplosion ||
                particles.value && it.packet is SPacketParticles ||
                packets
                && (lightning.value && it.packet is SPacketSpawnGlobalEntity ||
                    xp.value && it.packet is SPacketSpawnExperienceOrb ||
                    paint.value && it.packet is SPacketSpawnPainting)
            ) {
                it.cancel()
                return@listener
            }

            when (it.packet) {
                is SPacketSpawnObject -> {
                    it.cancelled = packets &&
                        when (it.packet.type) {
                            71 -> itemFrame.value
                            78 -> armorStand.value
                            51 -> crystal.value
                            2 -> items.value
                            70 -> falling.value
                            76 -> firework.value
                            else -> projectiles
                        }
                }
                is SPacketSpawnMob -> {
                    if (packets) {
                        val entityClass = GameData.getEntityRegistry().getValue(it.packet.entityType).entityClass
                        if (EntityMob::class.java.isAssignableFrom(entityClass)) {
                            if (mobs) it.cancel()
                        } else if (IAnimals::class.java.isAssignableFrom(entityClass)) {
                            if (animals) it.cancel()
                        }
                    }
                }
            }
        }

        listener<RenderEntityEvent.All> {
            if (it.phase != Phase.PRE) return@listener

            if (entityList.contains(it.entity.javaClass)
                || animals && it.entity is IAnimals && it.entity !is EntityMob
                || mobs && it.entity is EntityMob) {
                it.cancel()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.END && items.value) {
                for (entity in world.loadedEntityList) {
                    if (entity !is EntityItem) continue
                    entity.setDead()
                }
            }
        }
    }

    fun handleLighting(lightType: EnumSkyBlock): Boolean {
        return isEnabled && (skylight && lightType == EnumSkyBlock.SKY || allLightingUpdates)
    }

    fun handleParticle(particle: Particle) = particles.value
        || firework.value && (particle is ParticleFirework.Overlay || particle is ParticleFirework.Spark || particle is ParticleFirework.Starter)

    fun tryReplaceEnchantingTable(tileEntity: TileEntity): Boolean {
        if (!enchantingTableSnow || tileEntity !is TileEntityEnchantmentTable) return false

        runSafe {
            val blockState = Blocks.SNOW_LAYER.defaultState.withProperty(BlockSnow.LAYERS, 7)
            world.setBlockState(tileEntity.pos, blockState)
            world.markTileEntityForRemoval(tileEntity)
        }

        return true
    }

    fun renderFakeMap() {
        val tessellator = Tessellator.getInstance()
        val bufBuilder = tessellator.buffer
        mc.textureManager.bindTexture(lambdaMap)

        bufBuilder.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        bufBuilder.pos(0.0, 128.0, -0.009999999776482582).tex(0.0, 1.0).endVertex()
        bufBuilder.pos(128.0, 128.0, -0.009999999776482582).tex(1.0, 1.0).endVertex()
        bufBuilder.pos(128.0, 0.0, -0.009999999776482582).tex(1.0, 0.0).endVertex()
        bufBuilder.pos(0.0, 0.0, -0.009999999776482582).tex(0.0, 0.0).endVertex()

        tessellator.draw()
    }

    private fun updateList() {
        entityList = HashSet<Class<out Any>>().apply {
            settingMap.forEach {
                if (it.key.value) add(it.value)
            }
            // needed because there are 2 entities, the gateway and the portal
            if (endPortal.value) {
                add(TileEntityEndGateway::class.java)
            }
        }
    }

    init {
        val listener = { updateList() }
        settingMap.keys.forEach { it.listeners.add(listener) }
    }

}