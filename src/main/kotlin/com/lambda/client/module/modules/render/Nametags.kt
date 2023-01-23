package com.lambda.client.module.modules.render

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.module.modules.client.Hud
import com.lambda.client.module.modules.combat.TotemPopCounter
import com.lambda.client.module.modules.misc.LogoutLogger
import com.lambda.client.util.EnchantmentUtils
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.color.ColorGradient
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.*
import com.lambda.client.util.graphics.font.*
import com.lambda.client.util.items.originalName
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAir
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
    private val tamable by setting("Tamable Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val neutral by setting("Neutral Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val hostile by setting("Hostile Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val invisible by setting("Invisible", true, { page == Page.ENTITY_TYPE })
    private val range by setting("Range", 64, 0..256, 4, { page == Page.ENTITY_TYPE })

    private val line1left = setting("Line 1 Left", ContentType.NONE, { page == Page.CONTENT })
    private val line1center = setting("Line 1 Center", ContentType.NONE, { page == Page.CONTENT })
    private val line1right = setting("Line 1 Right", ContentType.NONE, { page == Page.CONTENT })
    private val line2left = setting("Line 2 Left", ContentType.NAME, { page == Page.CONTENT })
    private val line2center = setting("Line 2 Center", ContentType.PING, { page == Page.CONTENT })
    private val line2right = setting("Line 2 Right", ContentType.TOTAL_HP, { page == Page.CONTENT })
    private val dropItemCount by setting("Drop Item Count", true, { page == Page.CONTENT && items })
    private val maxDropItems by setting("Max Drop Items", 5, 2..16, 1, { page == Page.CONTENT && items })

    /* Item */
    private val mainHand by setting("Main Hand", true, { page == Page.ITEM })
    private val offhand by setting("Off Hand", true, { page == Page.ITEM })
    private val invertHand by setting("Invert Hand", false, { page == Page.ITEM && (mainHand || offhand) })
    private val armor by setting("Armor", true, { page == Page.ITEM })
    private val count by setting("Count", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val durability by setting("Durability", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val enchantment by setting("Enchantment", true, { page == Page.ITEM && (mainHand || offhand || armor) })
    private val itemScale by setting("Item Scale", 1f, 0.25f..2f, 0.25f, { page == Page.ITEM })

    /* Rendering */
    private val background by setting("Background", true, { page == Page.RENDERING })
    private val outline by setting("Outline", ClickGUI.windowOutline, { page == Page.RENDERING })
    private val alpha by setting("Background Alpha", 150, 0..255, 1, { page == Page.RENDERING })
    private val margins by setting("Margins", 2.0f, 0.0f..10.0f, 0.1f, { page == Page.RENDERING })
    private val yOffset by setting("Y Offset", 0.5f, -2.5f..2.5f, 0.05f, { page == Page.RENDERING })
    private val scale by setting("Scale", 1f, 0.25f..5f, 0.25f, { page == Page.RENDERING })
    private val distScaleFactor by setting("Distance Scale Factor", 0.05f, 0.0f..1.0f, 0.05f, { page == Page.RENDERING })
    private val minDistScale by setting("Min Distance Scale", 0.35f, 0.0f..1.0f, 0.05f, { page == Page.RENDERING })

    private enum class Page {
        ENTITY_TYPE, CONTENT, ITEM, RENDERING
    }

    private enum class ContentType {
        NONE, NAME, TYPE, TOTAL_HP, HP, ABSORPTION, PING, DISTANCE, ENTITY_ID, TOTEM_POP_COUNT
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

                drawNametag(screenPos, (scale * 2f) * distFactor, xRange, yRange, vertexHelper, textComponent)
                drawItems(screenPos, (scale * 2f) * distFactor, vertexHelper, entity, textComponent)
            }

            for (itemGroup in itemMap) {
                val pos = itemGroup.getCenter(LambdaTessellator.pTicks()).add(0.0, yOffset.toDouble(), 0.0)
                val screenPos = ProjectionUtils.toScreenPos(pos)
                val dist = camPos.distanceTo(pos).toFloat() * 0.2f
                val distFactor = if (distScaleFactor == 0f) 1f else max(1f / (dist * distScaleFactor + 1f), minDistScale)

                drawNametag(screenPos, (scale * 2f) * distFactor, xRange, yRange, vertexHelper, itemGroup.textComponent)
            }

            GlStateUtils.rescaleMc()
        }
    }

    private fun drawNametag(screenPos: Vec3d, scale: Float, xRange: IntRange, yRange: IntRange, vertexHelper: VertexHelper, textComponent: TextComponent): Boolean {
        val halfWidth = textComponent.getWidth(CustomFont.isEnabled) / 2.0 + margins + 2.0
        val halfHeight = textComponent.getHeight(2, true, CustomFont.isEnabled) / 2.0 + margins + 2.0

        val scaledHalfWidth = halfWidth * scale
        val scaledHalfHeight = halfHeight * scale

        if ((screenPos.x - scaledHalfWidth).floorToInt() !in xRange
            && (screenPos.x + scaledHalfWidth).ceilToInt() !in xRange
            || (screenPos.y - scaledHalfHeight).floorToInt() !in yRange
            && (screenPos.y + scaledHalfHeight).ceilToInt() !in yRange) return false

        glPushMatrix()
        glTranslatef(screenPos.x.toFloat(), screenPos.y.toFloat(), 0f)
        glScalef(scale, scale, 1f)
        drawFrame(vertexHelper, Vec2d(-halfWidth, -halfHeight), Vec2d(halfWidth, halfHeight))
        textComponent.draw(skipEmptyLine = true, horizontalAlign = HAlign.CENTER, verticalAlign = VAlign.CENTER, customFont = CustomFont.isEnabled)
        glPopMatrix()

        return true
    }

    private fun drawItems(screenPos: Vec3d, nameTagScale: Float, vertexHelper: VertexHelper, entity: Entity, textComponent: TextComponent) {
        if (entity !is EntityLivingBase) return
        val itemList = ArrayList<Pair<ItemStack, TextComponent>>()

        getEnumHand(if (invertHand) EnumHandSide.RIGHT else EnumHandSide.LEFT)?.let { // Hand
            val itemStack = entity.getHeldItem(it)
            if (itemStack.item !is ItemAir) itemList.add(itemStack to getEnchantmentText(itemStack))
        }

        if (armor) for (armor in entity.armorInventoryList.reversed()) { // Armor
            if (armor.item !is ItemAir) itemList.add(armor to getEnchantmentText(armor))
        }

        getEnumHand(if (invertHand) EnumHandSide.LEFT else EnumHandSide.RIGHT)?.let { // Hand
            val itemStack = entity.getHeldItem(it)
            if (itemStack.item !is ItemAir) itemList.add(itemStack to getEnchantmentText(itemStack))
        }

        if (itemList.isEmpty()) return
        val halfHeight = textComponent.getHeight(2, true, CustomFont.isEnabled) / 2.0 + margins + 2.0
        val halfWidth = (itemList.count { !it.first.isEmpty } * 28) / 2f

        glPushMatrix()
        glTranslatef(screenPos.x.toFloat(), screenPos.y.toFloat(), 0f) // Translate to nametag pos
        glScalef(nameTagScale, nameTagScale, 1f) // Scale to nametag scale
        glTranslated(0.0, -ceil(halfHeight), 0.0) // Translate to top of nametag
        glScalef((itemScale * 2f) / nameTagScale, (itemScale * 2f) / nameTagScale, 1f) // Scale to item scale
        glTranslatef(0f, -4f, 0f)

        val drawDurability = durability && itemList.firstOrNull { it.first.isItemStackDamageable } != null

        glTranslatef(0f, -margins, 0f)
        val durabilityHeight = if (drawDurability) FontRenderAdapter.getFontHeight(customFont = CustomFont.isEnabled) + 2f else 0f
        val enchantmentHeight = if (enchantment) {
            (itemList.maxOfOrNull { it.second.getHeight(2, customFont = isEnabled) } ?: 0f) + 4f
        } else {
            0f
        }
        val height = 16 + durabilityHeight + enchantmentHeight * 0.6f
        val posBegin = Vec2d(-halfWidth - margins.toDouble(), -height - margins.toDouble())
        val posEnd = Vec2d(halfWidth + margins.toDouble(), margins.toDouble())
        drawFrame(vertexHelper, posBegin, posEnd)

        glTranslatef(-halfWidth + 4f, -16f, 0f)
        if (drawDurability) glTranslatef(0f, -FontRenderAdapter.getFontHeight(customFont = CustomFont.isEnabled) - 2f, 0f)

        for ((itemStack, enchantmentText) in itemList) {
            if (itemStack.isEmpty) continue
            drawItem(itemStack, enchantmentText, drawDurability)
        }
        glColor4f(1f, 1f, 1f, 1f)

        glPopMatrix()
    }

    private fun drawItem(itemStack: ItemStack, enchantmentText: TextComponent, drawDurability: Boolean) {
        GlStateUtils.blend(true)
        GlStateUtils.depth(true)
        mc.renderItem.zLevel = -100f
        RenderHelper.enableGUIStandardItemLighting()
        mc.renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0)
        RenderHelper.disableStandardItemLighting()
        mc.renderItem.zLevel = 0f
        glColor4f(1f, 1f, 1f, 1f)

        if (drawDurability && itemStack.isItemStackDamageable) {
            val durabilityPercentage = 100f - (itemStack.itemDamage.toFloat() / itemStack.maxDamage.toFloat()) * 100f
            val color = healthColorGradient.get(durabilityPercentage)
            val text = durabilityPercentage.roundToInt().toString()
            val textWidth = FontRenderAdapter.getStringWidth(text, customFont = CustomFont.isEnabled)
            FontRenderAdapter.drawString(text, 8f - textWidth / 2f, 17f, color = color, customFont = CustomFont.isEnabled)
        }

        if (count && itemStack.count > 1) {
            val itemCount = itemStack.count.toString()
            glTranslatef(0f, 0f, 60f)
            val stringWidth = 17f - FontRenderAdapter.getStringWidth(itemCount, customFont = CustomFont.isEnabled)
            FontRenderAdapter.drawString(itemCount, stringWidth, 9f, customFont = CustomFont.isEnabled)
            glTranslatef(0f, 0f, -60f)
        }

        glTranslatef(0f, -2f, 0f)
        if (enchantment) {
            val scale = if (CustomFont.isEnabled) 0.6f else 0.5f
            enchantmentText.draw(lineSpace = 2, scale = scale, verticalAlign = VAlign.BOTTOM, customFont = CustomFont.isEnabled)
        }

        glTranslatef(28f, 2f, 0f)
    }

    private fun getEnchantmentText(itemStack: ItemStack): TextComponent {
        val textComponent = TextComponent()
        val enchantmentList = EnchantmentUtils.getAllEnchantments(itemStack)
        val style = if (CustomFont.isEnabled) Style.BOLD else Style.REGULAR
        for (leveledEnchantment in enchantmentList) {
            textComponent.add(leveledEnchantment.alias, Hud.primaryColor, style)
            textComponent.addLine(leveledEnchantment.levelText, Hud.secondaryColor, style)
        }
        return textComponent
    }

    private fun getEnumHand(enumHandSide: EnumHandSide) =
        if (mc.gameSettings.mainHand == enumHandSide && mainHand) EnumHand.MAIN_HAND
        else if (mc.gameSettings.mainHand != enumHandSide && offhand) EnumHand.OFF_HAND
        else null

    private fun drawFrame(vertexHelper: VertexHelper, posBegin: Vec2d, posEnd: Vec2d) {
        if (ClickGUI.radius == 0.0) {
            if (background)
                RenderUtils2D.drawRectFilled(
                    vertexHelper,
                    posBegin,
                    posEnd,
                    GuiColors.backGround.apply { a = alpha }
                )
            if (outline && ClickGUI.outlineWidth != 0f)
                RenderUtils2D.drawRectOutline(
                    vertexHelper,
                    posBegin,
                    posEnd,
                    ClickGUI.outlineWidth,
                    GuiColors.outline
                )
        } else {
            if (background)
                RenderUtils2D.drawRoundedRectFilled(
                    vertexHelper,
                    posBegin,
                    posEnd,
                    ClickGUI.radius,
                    8,
                    GuiColors.backGround.apply { a = alpha }
                )
            if (outline && ClickGUI.outlineWidth != 0f)
                RenderUtils2D.drawRoundedRectOutline(
                    vertexHelper,
                    posBegin,
                    posEnd,
                    ClickGUI.radius,
                    8,
                    ClickGUI.outlineWidth,
                    GuiColors.outline
                )
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

                    LogoutLogger.loggedOutPlayers.values.filter {
                        player.getDistance(it) <= range
                    }.forEach {
                        entityMap[it] = TextComponent()
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
                        getContent(contentType.value, entity)?.let {
                            textComponent.add(it)
                            isLine1Empty = false
                        }
                    }
                    if (!isLine1Empty) textComponent.currentLine++
                    for (contentType in line2Settings) {
                        getContent(contentType.value, entity)?.let {
                            textComponent.add(it)
                        }
                    }
                }
            }
        }
    }

    private fun SafeClientEvent.getContent(contentType: ContentType, entity: Entity) = when (contentType) {
        ContentType.NONE -> {
            null
        }
        ContentType.NAME -> {
            val name = entity.displayName.unformattedText
            TextComponent.TextElement(name, GuiColors.text)
        }
        ContentType.TYPE -> {
            TextComponent.TextElement(getEntityType(entity), GuiColors.text)
        }
        ContentType.TOTAL_HP -> {
            if (entity is EntityLivingBase) {
                val totalHp = MathUtils.round(entity.health + entity.absorptionAmount, 1).toString()
                TextComponent.TextElement(totalHp, getHpColor(entity))
            } else {
                null
            }
        }
        ContentType.HP -> {
            if (entity is EntityLivingBase) {
                val hp = MathUtils.round(entity.health, 1).toString()
                TextComponent.TextElement(hp, getHpColor(entity))
            } else {
                null
            }
        }
        ContentType.ABSORPTION -> {
            if (entity is EntityLivingBase && entity.absorptionAmount != 0f) {
                val absorption = MathUtils.round(entity.absorptionAmount, 1).toString()
                TextComponent.TextElement(absorption, ColorHolder(234, 204, 32, GuiColors.text.a))
            } else {
                null
            }
        }
        ContentType.PING -> {
            if (entity is EntityOtherPlayerMP) {
                connection.getPlayerInfo(entity.uniqueID)?.responseTime?.let {
                    TextComponent.TextElement("${it}ms", pingColorGradient.get(it.toFloat()).apply { a = GuiColors.text.a })
                }
            } else {
                null
            }
        }
        ContentType.DISTANCE -> {
            val dist = MathUtils.round(player.getDistance(entity), 1).toString()
            TextComponent.TextElement("${dist}m", GuiColors.text)
        }
        ContentType.ENTITY_ID -> {
            TextComponent.TextElement("ID: ${entity.entityId}", GuiColors.text)
        }
        ContentType.TOTEM_POP_COUNT -> {
            // Note: The totem pop counting functionality is embedded in the TotemPopCounter module,
            //       hence, it needs to be active in order for this to work.
            if (entity is EntityOtherPlayerMP) {
                if (TotemPopCounter.isDisabled) TotemPopCounter.enable()
                val count = TotemPopCounter.popCountMap.getOrDefault(entity, 0)
                TextComponent.TextElement("PT: $count", GuiColors.text)
            } else {
                null
            }
        }
    }

    private fun getEntityType(entity: Entity) = entity.javaClass.simpleName.replace("Entity", "")
        .replace("Other", "")
        .replace("MP", "")
        .replace("SP", "")
        .replace(" ", "")

    private fun getHpColor(entity: EntityLivingBase) = healthColorGradient.get((entity.health / entity.maxHealth) * 100f).apply { a = GuiColors.text.a }

    fun checkEntityType(entity: Entity) = (self || entity != mc.renderViewEntity)
        && (!entity.isInvisible || invisible)
        && (entity is EntityXPOrb && experience
        || entity is EntityPlayer && players && EntityUtils.playerTypeCheck(entity, friend = true, sleeping = true)
        || EntityUtils.mobTypeSettings(entity, mobs, passive, neutral, hostile, tamable))

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
            itemSet.removeAll(toRemove.toSet())
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
                textComponent.addLine(text, GuiColors.text)
                if (index + 1 >= maxDropItems) {
                    val remaining = itemCountMap.size - index - 1
                    if (remaining > 0) textComponent.addLine("...and $remaining more", GuiColors.text)
                    break
                }
            }
        }
    }
}
