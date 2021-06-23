package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.combat.TotemPopCounter
import com.lambda.client.util.EnchantmentUtils
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.color.ColorGradient
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.*
import com.lambda.client.util.graphics.font.*
import com.lambda.client.util.items.originalName
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.commons.extension.ceilToInt
import com.lambda.commons.extension.floorToInt
import com.lambda.commons.utils.MathUtils
import com.lambda.event.listener.listener
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumHandSide
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object Nametags : Module(
    name = "Nametags",
    description = "Draws descriptive nametags above entities",
    category = Category.RENDER
) {
    private val page by setting("Page", Page.ENTITY_TYPE)

    /* Entity type settings */
    private val self by setting("Self", false, { page == Page.ENTITY_TYPE })
    private val experience by setting("Experience", false, { page == Page.ENTITY_TYPE })
    private val items by setting("Items", true, { page == Page.ENTITY_TYPE })
    private val players by setting("Players", true, { page == Page.ENTITY_TYPE })
    private val mobs by setting("Mobs", true, { page == Page.ENTITY_TYPE })
    private val passive by setting("Passive Mobs", false, { page == Page.ENTITY_TYPE && mobs })
    private val neutral by setting("Neutral Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val hostile by setting("Hostile Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val invisible by setting("Invisible", true, { page == Page.ENTITY_TYPE })
    private val range by setting("Range", 64, 0..256, 4, { page == Page.ENTITY_TYPE })

    /* Content */
    private val line1left by setting("Line 1 Left", ContentType.NONE, { page == Page.CONTENT })
    private val line1center by setting("Line 1 Center", ContentType.NONE, { page == Page.CONTENT })
    private val line1right by setting("Line 1 Right", ContentType.NONE, { page == Page.CONTENT })
    private val line2left by setting("Line 2 Left", ContentType.NAME, { page == Page.CONTENT })
    private val line2center by setting("Line 2 Center", ContentType.PING, { page == Page.CONTENT })
    private val line2right by setting("Line 2 Right", ContentType.TOTAL_HP, { page == Page.CONTENT })
    private val dropItemCount by setting("Drop Item Count", true, { page == Page.CONTENT && items })
    private val maxDropItems by setting("Max Drop Items", 5, 2..16, 1, { page == Page.CONTENT && items })

    /* Item */
    private val mainHand by setting("Main Hand", true, { page == Page.ITEM })
    private val offhand by setting("Off Hand", true, { page == Page.ITEM })
    private val invertHand by setting("Invert Hand", false, { page == Page.ITEM && (mainHand || offhand) })
    private val armor by setting("Armor", true, { page == Page.ITEM })
    private val count by setting("Count", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val dura by setting("Dura", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val enchantment by setting("Enchantment", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val itemScale by setting("Item Scale", 1f, 0.25f..2f, 0.25f, { page == Page.ITEM })

    /* Frame */
    private val nameFrame by setting("Name Frame", true, { page == Page.FRAME })
    private val itemFrame by setting("Item Frame", false, { page == Page.FRAME })
    private val dropItemFrame by setting("Drop Item Frame", true, { page == Page.FRAME })
    private val filled by setting("Filled", true, { page == Page.FRAME })
    private val colorFilled by setting("Color Filled", ColorHolder(39, 36, 64, 169), visibility = { page == Page.FRAME && filled })
    private val outline by setting("Outline", true, { page == Page.FRAME })
    private val colorOutline by setting("Color Outline", ColorHolder(155, 144, 255, 240), visibility = { page == Page.FRAME && outline })
    private val outlineWidth by setting("Outline Width", 2.0f, 0.0f..5.0f, 0.1f, { page == Page.FRAME && outline })
    private val margins by setting("Margins", 2.0f, 0.0f..10.0f, 0.1f, { page == Page.FRAME })
    private val cornerRadius by setting("Corner Radius", 2.0f, 0.0f..10.0f, 0.1f, { page == Page.FRAME })

    /* Rendering settings */
    private val colorText by setting("Text Color", ColorHolder(232, 229, 255, 255), visibility = { page == Page.RENDERING })
    private val customFont by setting("Custom Font", true, { page == Page.RENDERING })
    private val yOffset by setting("Y Offset", 0.5f, -2.5f..2.5f, 0.05f, { page == Page.RENDERING })
    private val scale by setting("Scale", 1f, 0.25f..5f, 0.25f, { page == Page.RENDERING })
    private val distScaleFactor by setting("Distance Scale Factor", 0.0f, 0.0f..1.0f, 0.05f, { page == Page.RENDERING })
    private val minDistScale by setting("Min Distance Scale", 0.35f, 0.0f..1.0f, 0.05f, { page == Page.RENDERING })

    private enum class Page {
        ENTITY_TYPE, CONTENT, ITEM, FRAME, RENDERING
    }

    private enum class ContentType {
        NONE, NAME, TYPE, TOTAL_HP, HP, ABSORPTION, PING, DISTANCE, TOTEM_POPS
    }

    private val pingColorGradient = ColorGradient(
        0f to ColorHolder(101, 101, 101),
        0.1f to ColorHolder(20, 232, 20),
        20f to ColorHolder(20, 232, 20),
        150f to ColorHolder(20, 232, 20),
        300f to ColorHolder(150, 0, 0)
    )

    private val healthColorGradient = ColorGradient(
        0f to ColorHolder(180, 20, 20),
        50f to ColorHolder(240, 220, 20),
        100f to ColorHolder(20, 232, 20)
    )

    private val line1Settings = arrayOf(line1left, line1center, line1right)
    private val line2Settings = arrayOf(line2left, line2center, line2right)
    private val entityMap = TreeMap<Entity, TextComponent>(compareByDescending { mc.player.getPositionEyes(1f).distanceTo(it.getPositionEyes(1f)) })
    private val itemMap = TreeSet<ItemGroup>(compareByDescending { mc.player.getPositionEyes(1f).distanceTo(it.getCenter(1f)) })

    private var updateTick = 0

    init {
        listener<RenderOverlayEvent> {
            if (entityMap.isEmpty() && itemMap.isEmpty()) return@listener
            GlStateUtils.rescaleActual()

            val camPos = LambdaTessellator.camPos
            val vertexHelper = VertexHelper(GlStateUtils.useVbo())
            val xRange = 0..mc.displayWidth
            val yRange = 0..mc.displayHeight

            for ((entity, textComponent) in entityMap) {
                val pos = EntityUtils.getInterpolatedPos(entity, LambdaTessellator.pTicks()).add(0.0, (entity.height + yOffset).toDouble(), 0.0)
                val screenPos = ProjectionUtils.toScreenPos(pos)
                val dist = camPos.distanceTo(pos).toFloat() * 0.2f
                val distFactor = if (distScaleFactor == 0f) 1f else max(1f / (dist * distScaleFactor + 1f), minDistScale)

                if (drawNametag(screenPos, (scale * 2f) * distFactor, xRange, yRange, vertexHelper, nameFrame, textComponent)) {
                    drawItems(screenPos, (scale * 2f) * distFactor, vertexHelper, entity, textComponent)
                }
            }

            for (itemGroup in itemMap) {
                val pos = itemGroup.getCenter(LambdaTessellator.pTicks()).add(0.0, yOffset.toDouble(), 0.0)
                val screenPos = ProjectionUtils.toScreenPos(pos)
                val dist = camPos.distanceTo(pos).toFloat() * 0.2f
                val distFactor = if (distScaleFactor == 0f) 1f else max(1f / (dist * distScaleFactor + 1f), minDistScale)

                drawNametag(screenPos, (scale * 2f) * distFactor, xRange, yRange, vertexHelper, dropItemFrame, itemGroup.textComponent)
            }

            GlStateUtils.rescaleMc()
        }
    }

    private fun drawNametag(screenPos: Vec3d, scale: Float, xRange: IntRange, yRange: IntRange, vertexHelper: VertexHelper, drawFrame: Boolean, textComponent: TextComponent): Boolean {
        val halfWidth = textComponent.getWidth(customFont) / 2.0 + margins + 2.0
        val halfHeight = textComponent.getHeight(2, true, customFont) / 2.0 + margins + 2.0

        val scaledHalfWidth = halfWidth * scale
        val scaledHalfHeight = halfHeight * scale

        if ((screenPos.x - scaledHalfWidth).floorToInt() !in xRange
            && (screenPos.x + scaledHalfWidth).ceilToInt() !in xRange
            || (screenPos.y - scaledHalfHeight).floorToInt() !in yRange
            && (screenPos.y + scaledHalfHeight).ceilToInt() !in yRange) return false

        glPushMatrix()
        glTranslatef(screenPos.x.toFloat(), screenPos.y.toFloat(), 0f)
        glScalef(scale, scale, 1f)
        if (drawFrame) drawFrame(vertexHelper, Vec2d(-halfWidth, -halfHeight), Vec2d(halfWidth, halfHeight))
        textComponent.draw(skipEmptyLine = true, horizontalAlign = HAlign.CENTER, verticalAlign = VAlign.CENTER, customFont = customFont)
        glPopMatrix()

        return true
    }

    private fun drawItems(screenPos: Vec3d, nameTagScale: Float, vertexHelper: VertexHelper, entity: Entity, textComponent: TextComponent) {
        if (entity !is EntityLivingBase) return
        val itemList = ArrayList<Pair<ItemStack, TextComponent>>()

        getEnumHand(if (invertHand) EnumHandSide.RIGHT else EnumHandSide.LEFT)?.let { // Hand
            val itemStack = entity.getHeldItem(it)
            itemList.add(itemStack to getEnchantmentText(itemStack))
        }

        if (armor) for (armor in entity.armorInventoryList.reversed()) itemList.add(armor to getEnchantmentText(armor)) // Armor

        getEnumHand(if (invertHand) EnumHandSide.LEFT else EnumHandSide.RIGHT)?.let { // Hand
            val itemStack = entity.getHeldItem(it)
            itemList.add(itemStack to getEnchantmentText(itemStack))
        }

        if (itemList.isEmpty() || itemList.count { !it.first.isEmpty } == 0) return
        val halfHeight = textComponent.getHeight(2, true, customFont) / 2.0 + margins + 2.0
        val halfWidth = (itemList.count { !it.first.isEmpty } * 28) / 2f

        glPushMatrix()
        glTranslatef(screenPos.x.toFloat(), screenPos.y.toFloat(), 0f) // Translate to nametag pos
        glScalef(nameTagScale, nameTagScale, 1f) // Scale to nametag scale
        glTranslated(0.0, -ceil(halfHeight), 0.0) // Translate to top of nametag
        glScalef((itemScale * 2f) / nameTagScale, (itemScale * 2f) / nameTagScale, 1f) // Scale to item scale
        glTranslatef(0f, -4f, 0f)

        val drawDura = dura && itemList.firstOrNull { it.first.isItemStackDamageable } != null

        if (itemFrame) {
            glTranslatef(0f, -margins, 0f)
            val duraHeight = if (drawDura) FontRenderAdapter.getFontHeight(customFont = customFont) + 2f else 0f
            val enchantmentHeight = if (enchantment) {
                (itemList.map { it.second.getHeight(2, customFont = customFont) }.maxOrNull() ?: 0f) + 4f
            } else {
                0f
            }
            val height = 16 + duraHeight + enchantmentHeight * 0.6f
            val posBegin = Vec2d(-halfWidth - margins.toDouble(), -height - margins.toDouble())
            val posEnd = Vec2d(halfWidth + margins.toDouble(), margins.toDouble())
            drawFrame(vertexHelper, posBegin, posEnd)
        }

        glTranslatef(-halfWidth + 4f, -16f, 0f)
        if (drawDura) glTranslatef(0f, -FontRenderAdapter.getFontHeight(customFont = customFont) - 2f, 0f)

        for ((itemStack, enchantmentText) in itemList) {
            if (itemStack.isEmpty) continue
            drawItem(itemStack, enchantmentText, drawDura)
        }
        glColor4f(1f, 1f, 1f, 1f)

        glPopMatrix()
    }

    private fun drawItem(itemStack: ItemStack, enchantmentText: TextComponent, drawDura: Boolean) {
        GlStateUtils.blend(true)
        GlStateUtils.depth(true)
        mc.renderItem.zLevel = -100f
        RenderHelper.enableGUIStandardItemLighting()
        mc.renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0)
        RenderHelper.disableStandardItemLighting()
        mc.renderItem.zLevel = 0f
        glColor4f(1f, 1f, 1f, 1f)

        if (drawDura && itemStack.isItemStackDamageable) {
            val duraPercentage = 100f - (itemStack.itemDamage.toFloat() / itemStack.maxDamage.toFloat()) * 100f
            val color = healthColorGradient.get(duraPercentage)
            val text = duraPercentage.roundToInt().toString()
            val textWidth = FontRenderAdapter.getStringWidth(text, customFont = customFont)
            FontRenderAdapter.drawString(text, 8f - textWidth / 2f, 17f, color = color, customFont = customFont)
        }

        if (count && itemStack.count > 1) {
            val itemCount = itemStack.count.toString()
            glTranslatef(0f, 0f, 60f)
            val stringWidth = 17f - FontRenderAdapter.getStringWidth(itemCount, customFont = customFont)
            FontRenderAdapter.drawString(itemCount, stringWidth, 9f, customFont = customFont)
            glTranslatef(0f, 0f, -60f)
        }

        glTranslatef(0f, -2f, 0f)
        if (enchantment) {
            val scale = if (customFont) 0.6f else 0.5f
            enchantmentText.draw(lineSpace = 2, scale = scale, verticalAlign = VAlign.BOTTOM, customFont = customFont)
        }

        glTranslatef(28f, 2f, 0f)
    }

    private fun getEnchantmentText(itemStack: ItemStack): TextComponent {
        val textComponent = TextComponent()
        val enchantmentList = EnchantmentUtils.getAllEnchantments(itemStack)
        val style = if (customFont) Style.BOLD else Style.REGULAR
        for (leveledEnchantment in enchantmentList) {
            textComponent.add(leveledEnchantment.alias, ColorHolder(255, 255, 255, colorText.a), style)
            textComponent.addLine(leveledEnchantment.levelText, ColorHolder(155, 144, 255, colorText.a), style)
        }
        return textComponent
    }

    private fun getEnumHand(enumHandSide: EnumHandSide) =
        if (mc.gameSettings.mainHand == enumHandSide && mainHand) EnumHand.MAIN_HAND
        else if (mc.gameSettings.mainHand != enumHandSide && offhand) EnumHand.OFF_HAND
        else null

    private fun drawFrame(vertexHelper: VertexHelper, posBegin: Vec2d, posEnd: Vec2d) {
        if (cornerRadius == 0f) {
            if (filled)
                RenderUtils2D.drawRectFilled(vertexHelper, posBegin, posEnd, colorFilled)
            if (outline && outlineWidth != 0f)
                RenderUtils2D.drawRectOutline(vertexHelper, posBegin, posEnd, outlineWidth, colorOutline)
        } else {
            if (filled)
                RenderUtils2D.drawRoundedRectFilled(vertexHelper, posBegin, posEnd, cornerRadius.toDouble(), 8, colorFilled)
            if (outline && outlineWidth != 0f)
                RenderUtils2D.drawRoundedRectOutline(vertexHelper, posBegin, posEnd, cornerRadius.toDouble(), 8, outlineWidth, colorOutline)
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            // Updating stuff in different ticks to avoid overloading
            when (updateTick) {
                0 -> { // Adding items
                    if (!items) {
                        itemMap.clear()
                    } else {
                        loop@ for (entity in world.loadedEntityList) {
                            if (entity !is EntityItem) continue
                            if (player.getDistance(entity) > range) continue
                            for (itemGroup in itemMap) {
                                if (itemGroup.contains(entity)) continue@loop // If we have this item already in the groups then we skip it
                            }
                            for (itemGroup in itemMap) {
                                if (itemGroup.add(entity)) continue@loop // If we add the item to any of the group successfully then we continue
                            }
                            ItemGroup().apply { add(entity) }.also { itemMap.add(it) } // If we can't find an existing group then we make a new one and add it to the map
                        }
                    }
                }
                1 -> { // Updating Entity
                    entityMap.clear()
                    for (entity in world.loadedEntityList) {
                        if (!checkEntityType(entity)) continue
                        if (entity is EntityItem) continue
                        if (player.getDistance(entity) > range) continue
                        entityMap[entity] = TextComponent()
                    }
                }
                2 -> { // Removing items
                    val loadEntitySet = world.loadedEntityList.toHashSet()
                    for (itemGroup in itemMap) {
                        itemGroup.updateItems(loadEntitySet)
                    }
                    itemMap.removeIf { it.isEmpty() }
                }
                3 -> { // Merging Items
                    for (itemGroup in itemMap) for (otherGroup in itemMap) {
                        if (itemGroup == otherGroup) continue
                        itemGroup.merge(otherGroup)
                    }
                    itemMap.removeIf { it.isEmpty() }
                }
            }
            updateTick = (updateTick + 1) % 4

            // Update item nametags tick by tick
            for (itemGroup in itemMap) {
                itemGroup.updateText()
            }

            // Update entity nametags tick by tick
            for ((entity, textComponent) in entityMap) {
                textComponent.clear()
                if (entity is EntityXPOrb) {
                    textComponent.add(entity.name)
                    textComponent.add(" x${entity.xpValue}")
                } else {
                    var isLine1Empty = true
                    for (contentType in line1Settings) {
                        getContent(contentType, entity)?.let {
                            textComponent.add(it)
                            isLine1Empty = false
                        }
                    }
                    if (!isLine1Empty) textComponent.currentLine++
                    for (contentType in line2Settings) {
                        getContent(contentType, entity)?.let {
                            textComponent.add(it)
                        }
                    }
                }
            }
        }
    }

    private fun getContent(contentType: ContentType, entity: Entity) = when (contentType) {
        ContentType.NONE -> {
            null
        }
        ContentType.NAME -> {
            val name = entity.displayName.unformattedText
            TextComponent.TextElement(name, colorText)
        }
        ContentType.TYPE -> {
            TextComponent.TextElement(getEntityType(entity), colorText)
        }
        ContentType.TOTAL_HP -> {
            if (entity !is EntityLivingBase) {
                null
            } else {
                val totalHp = MathUtils.round(entity.health + entity.absorptionAmount, 1).toString()
                TextComponent.TextElement(totalHp, getHpColor(entity))
            }
        }
        ContentType.HP -> {
            if (entity !is EntityLivingBase) {
                null
            } else {
                val hp = MathUtils.round(entity.health, 1).toString()
                TextComponent.TextElement(hp, getHpColor(entity))
            }
        }
        ContentType.ABSORPTION -> {
            if (entity !is EntityLivingBase || entity.absorptionAmount == 0f) {
                null
            } else {
                val absorption = MathUtils.round(entity.absorptionAmount, 1).toString()
                TextComponent.TextElement(absorption, ColorHolder(234, 204, 32, colorText.a))
            }
        }
        ContentType.PING -> {
            if (entity !is EntityOtherPlayerMP) {
                null
            } else {
                val ping = mc.connection?.getPlayerInfo(entity.uniqueID)?.responseTime ?: 0
                TextComponent.TextElement("${ping}ms", pingColorGradient.get(ping.toFloat()).apply { a = colorText.a })
            }
        }
        ContentType.DISTANCE -> {
            val dist = MathUtils.round(mc.player.getDistance(entity), 1).toString()
            TextComponent.TextElement("${dist}m", colorText)
        }
       ContentType.TOTEM_POPS -> {
           if (!TotemPopCounter.isEnabled) {
               MessageSendHelper.sendWarningMessage("$chatName TotemPopCounter is locked on while nametags count totem pops.")
               TotemPopCounter.enable()
           }
           val totemPops = TotemPopCounter.popCountMap[entity] ?: "0"
           val pluralPops = if (totemPops == 1) "pop" else "pops"
           TextComponent.TextElement("$totemPops $pluralPops", colorText)
       }
    }

    private fun getEntityType(entity: Entity) = entity.javaClass.simpleName.replace("Entity", "")
        .replace("Other", "")
        .replace("MP", "")
        .replace("SP", "")
        .replace(" ", "")

    private fun getHpColor(entity: EntityLivingBase) = healthColorGradient.get((entity.health / entity.maxHealth) * 100f).apply { a = colorText.a }

    fun checkEntityType(entity: Entity) = (self || entity != mc.renderViewEntity)
        && (!entity.isInvisible || invisible)
        && (entity is EntityXPOrb && experience
        || entity is EntityPlayer && players && EntityUtils.playerTypeCheck(entity, friend = true, sleeping = true)
        || EntityUtils.mobTypeSettings(entity, mobs, passive, neutral, hostile))

    private class ItemGroup {
        private val itemSet = HashSet<EntityItem>()
        val textComponent = TextComponent()

        fun merge(other: ItemGroup) {
            val thisCenter = this.getCenter(1f)
            val otherCenter = other.getCenter(1f)
            val dist = thisCenter.distanceTo(otherCenter)
            if (dist < 8f) {
                val ableToMerge = ArrayList<EntityItem>()
                for (entityItem in other.itemSet) {
                    val pos = entityItem.positionVector
                    val distanceToThis = pos.distanceTo(thisCenter)
                    val distanceToOther = pos.distanceTo(otherCenter)
                    if (this.itemSet.size >= other.itemSet.size || distanceToThis < distanceToOther) ableToMerge.add(entityItem)
                }
                for (entityItem in ableToMerge) {
                    if (this.add(entityItem)) other.remove(entityItem)
                }
            }
        }

        fun add(item: EntityItem): Boolean {
            for (otherItem in itemSet) {
                if (otherItem.getDistance(item) > 4.0f) return false
            }
            return itemSet.add(item)
        }

        fun remove(item: EntityItem): Boolean {
            return itemSet.remove(item)
        }

        fun isEmpty() = itemSet.isEmpty()

        fun contains(item: EntityItem) = itemSet.contains(item)

        fun getCenter(partialTicks: Float): Vec3d {
            if (isEmpty()) return Vec3d.ZERO
            val sizeFactor = 1.0 / itemSet.size
            var center = Vec3d.ZERO
            for (entityItem in itemSet) {
                val pos = EntityUtils.getInterpolatedPos(entityItem, partialTicks)
                center = center.add(pos.scale(sizeFactor))
            }
            return center
        }

        fun updateItems(loadEntitySet: HashSet<Entity>) {
            // Removes items
            val toRemove = ArrayList<EntityItem>()
            for (entityItem in itemSet) {
                if (!entityItem.isAddedToWorld || entityItem.isDead || !loadEntitySet.contains(entityItem)) {
                    toRemove.add(entityItem)
                } else {
                    var remove = false
                    for (otherItem in itemSet) {
                        if (otherItem == entityItem) continue
                        if (otherItem.getDistance(entityItem) <= 4f) continue
                        remove = true
                    }
                    if (remove) toRemove.add(entityItem)
                }
            }
            itemSet.removeAll(toRemove)
        }

        fun updateText() {
            // Updates item count text
            val itemCountMap = TreeMap<String, Int>(Comparator.naturalOrder())
            for (entityItem in itemSet) {
                val itemStack = entityItem.item
                val originalName = itemStack.originalName
                val displayName = itemStack.displayName
                val finalName = if (displayName == originalName) originalName else "$displayName ($originalName)"
                val count = itemCountMap.getOrDefault(finalName, 0) + itemStack.count
                itemCountMap[finalName] = count
            }
            textComponent.clear()
            for ((index, entry) in itemCountMap.entries.sortedByDescending { it.value }.withIndex()) {
                val text = if (dropItemCount) "${entry.key} x${entry.value}" else entry.key
                textComponent.addLine(text, colorText)
                if (index + 1 >= maxDropItems) {
                    val remaining = itemCountMap.size - index - 1
                    if (remaining > 0) textComponent.addLine("...and $remaining more", colorText)
                    break
                }
            }
        }
    }
}
