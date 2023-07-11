package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.gui.hudgui.elements.misc.PacketLogViewer
import com.lambda.client.mixin.extension.*
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.potion.Potion
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.File
import java.io.FileWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PacketLogger : Module(
    name = "PacketLogger",
    description = "Logs sent packets to a file or chat",
    category = Category.PLAYER
) {
    private val page by setting("Page", Page.GENERAL)
    private val categorySetting by setting("Category", CategorySlider.PLAYER, { page == Page.CLIENT || page == Page.SERVER })
    private val packetSide by setting("Packet Side", PacketSide.BOTH, description = "Log packets from the server, from the client, or both.", visibility = { page == Page.GENERAL })
    private val absoluteTime by setting("Absolute Time", true, description = "Show absolute time.", visibility = { page == Page.GENERAL })
    private val startDelta by setting("Start Time Delta", false, visibility = { page == Page.GENERAL })
    private val lastDelta by setting("Last Time Delta", false, visibility = { page == Page.GENERAL })
    private val showClientTicks by setting("Show Client Ticks", false, description = "Show timestamps of client ticks.", visibility = { page == Page.GENERAL })
    private val ignoreCancelled by setting("Ignore Cancelled", true, description = "Ignore cancelled packets.", visibility = { page == Page.GENERAL })
    val logMode by setting("Log Mode", LogMode.ALL, description = "Log to chat, to a file, HUD, or both.", visibility = { page == Page.GENERAL })
    private val captureTiming by setting("Capture Timing", CaptureTiming.POST, description = "Sets point of time for scan event.", visibility = { page == Page.GENERAL })
    private val openLogFolder by setting("Open Log Folder...", false, consumer = { _, _ ->
        FolderUtils.openFolder(FolderUtils.packetLogFolder)
        false
    }, visibility = { page == Page.GENERAL })

    /**
     * Client Packets
     */
    private val toggleAllClientPackets by setting("Toggle All Client Packets", false, visibility = { page == Page.CLIENT }, consumer = { _, _ ->
        val toggleValue = anyClientPacketDisabled()
        cPacketAnimation = toggleValue
        cPacketChatMessage = toggleValue
        cPacketClickWindow = toggleValue
        cPacketClientSettings = toggleValue
        cPacketClientStatus = toggleValue
        cPacketCloseWindow = toggleValue
        cPacketConfirmTeleport = toggleValue
        cPacketConfirmTransaction = toggleValue
        cPacketCreativeInventoryAction = toggleValue
        cPacketCustomPayload = toggleValue
        cPacketEnchantItem = toggleValue
        cPacketEntityAction = toggleValue
        cPacketHeldItemChange = toggleValue
        cPacketInput = toggleValue
        cPacketKeepAlive = toggleValue
        cPacketPlaceRecipe = toggleValue
        cPacketPlayerRotation = toggleValue
        cPacketPlayerPosition = toggleValue
        cPacketPlayerPositionRotation = toggleValue
        cPacketPlayer = toggleValue
        cPacketPlayerAbilities = toggleValue
        cPacketPlayerDigging = toggleValue
        cPacketPlayerTryUseItem = toggleValue
        cPacketPlayerTryUseItemOnBlock = toggleValue
        cPacketRecipeInfo = toggleValue
        cPacketResourcePackStatus = toggleValue
        cPacketSeenAdvancements = toggleValue
        cPacketSpectate = toggleValue
        cPacketSteerBoat = toggleValue
        cPacketTabComplete = toggleValue
        cPacketUpdateSign = toggleValue
        cPacketUseEntity = toggleValue
        cPacketVehicleMove = toggleValue
        false
    })

    private fun anyClientPacketDisabled(): Boolean {
        return !cPacketAnimation || !cPacketChatMessage || !cPacketClickWindow || !cPacketClientSettings || !cPacketClientStatus || !cPacketCloseWindow
            || !cPacketConfirmTeleport || !cPacketConfirmTransaction || !cPacketCreativeInventoryAction || !cPacketCustomPayload || !cPacketEnchantItem
            || !cPacketEntityAction || !cPacketHeldItemChange || !cPacketInput || !cPacketKeepAlive || !cPacketPlaceRecipe
            || !cPacketPlayerRotation || !cPacketPlayerPosition || !cPacketPlayerPositionRotation || !cPacketPlayer
            || !cPacketPlayerAbilities || !cPacketPlayerDigging || !cPacketPlayerTryUseItem || !cPacketPlayerTryUseItemOnBlock || !cPacketRecipeInfo
            || !cPacketResourcePackStatus || !cPacketSeenAdvancements || !cPacketSpectate || !cPacketSteerBoat || !cPacketTabComplete
            || !cPacketUpdateSign || !cPacketUseEntity || !cPacketVehicleMove
    }

    /** Player **/
    private var cPacketAnimation by setting("CPacketAnimation", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketConfirmTeleport by setting("CPacketConfirmTeleport", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketInput by setting("CPacketInput", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerRotation by setting("CPacketPlayer.Rotation", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerPosition by setting("CPacketPlayer.Position", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerPositionRotation by setting("CPacketPlayer.PositionRotation", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayer by setting("CPacketPlayer", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerAbilities by setting("CPacketPlayerAbilities", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerDigging by setting("CPacketPlayerDigging", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerTryUseItem by setting("CPacketPlayerTryUseItem", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerTryUseItemOnBlock by setting("CPacketPlayerTryUseItemOnBlock", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketSpectate by setting("CPacketSpectate", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.PLAYER })

    /** Inventory **/
    private var cPacketClickWindow by setting("CPacketClickWindow", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketCloseWindow by setting("CPacketCloseWindow", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketConfirmTransaction by setting("CPacketConfirmTransaction", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketCreativeInventoryAction by setting("CPacketCreativeInventoryAction", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketEnchantItem by setting("CPacketEnchantItem", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketHeldItemChange by setting("CPacketHeldItemChange", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketPlaceRecipe by setting("CPacketPlaceRecipe", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketRecipeInfo by setting("CPacketRecipeInfo", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.INVENTORY })

    /** System **/
    private var cPacketChatMessage by setting("CPacketChatMessage", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketClientSettings by setting("CPacketClientSettings", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketClientStatus by setting("CPacketClientStatus", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketCustomPayload by setting("CPacketCustomPayload", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketKeepAlive by setting("CPacketKeepAlive", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketResourcePackStatus by setting("CPacketResourcePackStatus", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketSeenAdvancements by setting("CPacketSeenAdvancements", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketTabComplete by setting("CPacketTabComplete", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.SYSTEM })

    /** World **/
    private var cPacketUpdateSign by setting("CPacketUpdateSign", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.WORLD })

    /** Entity **/
    private var cPacketEntityAction by setting("CPacketEntityAction", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketSteerBoat by setting("CPacketSteerBoat", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketUseEntity by setting("CPacketUseEntity", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketVehicleMove by setting("CPacketVehicleMove", true, visibility = { page == Page.CLIENT && categorySetting == CategorySlider.ENTITY })


    /**
     * Server Packets
     */

    private val toggleAllServerPackets by setting("Toggle All Server Packets", false, visibility = { page == Page.SERVER }, consumer = { _, _ ->
        val toggleValue = anyServerPacketDisabled()
        sPacketAdvancementInfo = toggleValue
        sPacketAnimation = toggleValue
        sPacketBlockAction = toggleValue
        sPacketBlockBreakAnim = toggleValue
        sPacketBlockChange = toggleValue
        sPacketCamera = toggleValue
        sPacketChangeGameState = toggleValue
        sPacketChat = toggleValue
        sPacketChunkData = toggleValue
        sPacketCloseWindow = toggleValue
        sPacketCollectItem = toggleValue
        sPacketCombatEvent = toggleValue
        sPacketConfirmTransaction = toggleValue
        sPacketCooldown = toggleValue
        sPacketCustomPayload = toggleValue
        sPacketCustomSound = toggleValue
        sPacketDestroyEntities = toggleValue
        sPacketDisconnect = toggleValue
        sPacketDisplayObjective = toggleValue
        sPacketEffect = toggleValue
        s15PacketEntityRelMove = toggleValue
        s16PacketEntityLook = toggleValue
        s17PacketEntityLookMove = toggleValue
        sPacketEntity = toggleValue
        sPacketEntityAttach = toggleValue
        sPacketEntityEffect = toggleValue
        sPacketEntityEquipment = toggleValue
        sPacketEntityHeadLook = toggleValue
        sPacketEntityMetadata = toggleValue
        sPacketEntityProperties = toggleValue
        sPacketEntityStatus = toggleValue
        sPacketEntityTeleport = toggleValue
        sPacketEntityVelocity = toggleValue
        sPacketExplosion = toggleValue
        sPacketHeldItemChange = toggleValue
        sPacketJoinGame = toggleValue
        sPacketKeepAlive = toggleValue
        sPacketMaps = toggleValue
        sPacketMoveVehicle = toggleValue
        sPacketMultiBlockChange = toggleValue
        sPacketOpenWindow = toggleValue
        sPacketParticles = toggleValue
        sPacketPlaceGhostRecipe = toggleValue
        sPacketPlayerAbilities = toggleValue
        sPacketPlayerListHeaderFooter = toggleValue
        sPacketPlayerListItem = toggleValue
        sPacketPlayerPosLook = toggleValue
        sPacketRecipeBook = toggleValue
        sPacketRemoveEntityEffect = toggleValue
        sPacketResourcePackSend = toggleValue
        sPacketRespawn = toggleValue
        sPacketScoreboardObjective = toggleValue
        sPacketSelectAdvancementsTab = toggleValue
        sPacketServerDifficulty = toggleValue
        sPacketSetExperience = toggleValue
        sPacketSetPassengers = toggleValue
        sPacketSetSlot = toggleValue
        sPacketSignEditorOpen = toggleValue
        sPacketSoundEffect = toggleValue
        sPacketSpawnExperienceOrb = toggleValue
        sPacketSpawnGlobalEntity = toggleValue
        sPacketSpawnMob = toggleValue
        sPacketSpawnObject = toggleValue
        sPacketSpawnPainting = toggleValue
        sPacketSpawnPlayer = toggleValue
        sPacketSpawnPosition = toggleValue
        sPacketStatistics = toggleValue
        sPacketTabComplete = toggleValue
        sPacketTeams = toggleValue
        sPacketTimeUpdate = toggleValue
        sPacketTitle = toggleValue
        sPacketUnloadChunk = toggleValue
        sPacketUpdateBossInfo = toggleValue
        sPacketUpdateHealth = toggleValue
        sPacketUpdateScore = toggleValue
        sPacketUpdateTileEntity = toggleValue
        sPacketUseBed = toggleValue
        sPacketWindowItems = toggleValue
        sPacketWindowProperty = toggleValue
        sPacketWorldBorder = toggleValue
        false
    })

    private fun anyServerPacketDisabled() : Boolean {
        return !sPacketAdvancementInfo || !sPacketAnimation || !sPacketBlockAction || !sPacketBlockBreakAnim || !sPacketBlockChange
            || !sPacketCamera || !sPacketChangeGameState || !sPacketChat || !sPacketChunkData || !sPacketCloseWindow || !sPacketCollectItem
            || !sPacketCombatEvent || !sPacketConfirmTransaction || !sPacketCooldown || !sPacketCustomPayload || !sPacketCustomSound
            || !sPacketDestroyEntities || !sPacketDisconnect || !sPacketDisplayObjective || !sPacketEffect
            || !s15PacketEntityRelMove || !s16PacketEntityLook || !s17PacketEntityLookMove || !sPacketEntity || !sPacketEntityAttach
            || !sPacketEntityEffect || !sPacketEntityEquipment || !sPacketEntityHeadLook || !sPacketEntityMetadata || !sPacketEntityProperties
            || !sPacketEntityStatus || !sPacketEntityTeleport || !sPacketEntityVelocity || !sPacketExplosion || !sPacketHeldItemChange
            || !sPacketJoinGame || !sPacketKeepAlive || !sPacketMaps || !sPacketMoveVehicle || !sPacketMultiBlockChange || !sPacketOpenWindow
            || !sPacketParticles || !sPacketPlaceGhostRecipe || !sPacketPlayerAbilities || !sPacketPlayerListHeaderFooter || !sPacketPlayerListItem
            || !sPacketPlayerPosLook || !sPacketRecipeBook || !sPacketRemoveEntityEffect || !sPacketResourcePackSend || !sPacketRespawn
            || !sPacketScoreboardObjective || !sPacketSelectAdvancementsTab || !sPacketServerDifficulty || !sPacketSetExperience || !sPacketSetPassengers
            || !sPacketSetSlot || !sPacketSignEditorOpen || !sPacketSoundEffect || !sPacketSpawnExperienceOrb || !sPacketSpawnGlobalEntity
            || !sPacketSpawnMob || !sPacketSpawnObject || !sPacketSpawnPainting || !sPacketSpawnPlayer || !sPacketSpawnPosition || !sPacketStatistics
            || !sPacketTabComplete || !sPacketTeams || !sPacketTimeUpdate || !sPacketTitle || !sPacketUnloadChunk || !sPacketUpdateBossInfo
            || !sPacketUpdateHealth || !sPacketUpdateScore || !sPacketUpdateTileEntity || !sPacketUseBed || !sPacketWindowItems || !sPacketWindowProperty
            || !sPacketWorldBorder
    }

    /** Player **/
    private var sPacketAdvancementInfo by setting("SPacketAdvancementInfo", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketAnimation by setting("SPacketAnimation", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketBlockAction by setting("SPacketBlockAction", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketBlockBreakAnim by setting("SPacketBlockBreakAnim", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketCamera by setting("SPacketCamera", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketChangeGameState by setting("SPacketChangeGameState", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketCombatEvent by setting("SPacketCombatEvent", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketCooldown by setting("SPacketCooldown", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketPlayerAbilities by setting("SPacketPlayerAbilities", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketPlayerPosLook by setting("SPacketPlayerPosLook", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketRespawn by setting("SPacketRespawn", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketSetExperience by setting("SPacketSetExperience", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketUpdateHealth by setting("SPacketUpdateHealth", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketUpdateScore by setting("SPacketUpdateScore", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })
    private var sPacketUseBed by setting("SPacketUseBed", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.PLAYER })

    /** Inventory **/
    private var sPacketCloseWindow by setting("SPacketCloseWindow", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketConfirmTransaction by setting("SPacketConfirmTransaction", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketHeldItemChange by setting("SPacketHeldItemChange", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketOpenWindow by setting("SPacketOpenWindow", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketPlaceGhostRecipe by setting("SPacketPlaceGhostRecipe", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketRecipeBook by setting("SPacketRecipeBook", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketSetSlot by setting("SPacketSetSlot", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketWindowItems by setting("SPacketWindowItems", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var sPacketWindowProperty by setting("SPacketWindowProperty", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.INVENTORY })

    /** System **/
    private var sPacketChat by setting("SPacketChat", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketCustomPayload by setting("SPacketCustomPayload", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketCustomSound by setting("SPacketCustomSound", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketDisconnect by setting("SPacketDisconnect", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketDisplayObjective by setting("SPacketDisplayObjective", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketJoinGame by setting("SPacketJoinGame", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketKeepAlive by setting("SPacketKeepAlive", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketPlayerListHeaderFooter by setting("SPacketPlayerListHeaderFooter", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketPlayerListItem by setting("SPacketPlayerListItem", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketResourcePackSend by setting("SPacketResourcePackSend", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketScoreboardObjective by setting("SPacketScoreboardObjective", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketSelectAdvancementsTab by setting("SPacketSelectAdvancementsTab", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketStatistics by setting("SPacketStatistics", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketTabComplete by setting("SPacketTabComplete", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketTeams by setting("SPacketTeams", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var sPacketTitle by setting("SPacketTitle", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.SYSTEM })

    /** World **/
    private var sPacketBlockChange by setting("SPacketBlockChange", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketChunkData by setting("SPacketChunkData", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketEffect by setting("SPacketEffect", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketExplosion by setting("SPacketExplosion", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketMaps by setting("SPacketMaps", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketMultiBlockChange by setting("SPacketMultiBlockChange", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketParticles by setting("SPacketParticles", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketServerDifficulty by setting("SPacketServerDifficulty", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketSignEditorOpen by setting("SPacketSignEditorOpen", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketSoundEffect by setting("SPacketSoundEffect", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketSpawnPosition by setting("SPacketSpawnPosition", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketTimeUpdate by setting("SPacketTimeUpdate", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketUnloadChunk by setting("SPacketUnloadChunk", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketUpdateBossInfo by setting("SPacketUpdateBossInfo", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketUpdateTileEntity by setting("SPacketUpdateTileEntity", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })
    private var sPacketWorldBorder by setting("SPacketWorldBorder", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.WORLD })

    /** Entity **/
    private var sPacketCollectItem by setting("SPacketCollectItem", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketDestroyEntities by setting("SPacketDestroyEntities", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var s15PacketEntityRelMove by setting("S15PacketEntityRelMove", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var s16PacketEntityLook by setting("S16PacketEntityLook", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var s17PacketEntityLookMove by setting("S17PacketEntityLookMove", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntity by setting("SPacketEntity", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityAttach by setting("SPacketEntityAttach", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityEffect by setting("SPacketEntityEffect", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityEquipment by setting("SPacketEntityEquipment", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityHeadLook by setting("SPacketEntityHeadLook", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityMetadata by setting("SPacketEntityMetadata", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityProperties by setting("SPacketEntityProperties", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityStatus by setting("SPacketEntityStatus", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityTeleport by setting("SPacketEntityTeleport", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketEntityVelocity by setting("SPacketEntityVelocity", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketMoveVehicle by setting("SPacketMoveVehicle", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketRemoveEntityEffect by setting("SPacketRemoveEntityEffect", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSetPassengers by setting("SPacketSetPassengers", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnExperienceOrb by setting("SPacketSpawnExperienceOrb", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnGlobalEntity by setting("SPacketSpawnGlobalEntity", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnMob by setting("SPacketSpawnMob", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnObject by setting("SPacketSpawnObject", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnPainting by setting("SPacketSpawnPainting", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })
    private var sPacketSpawnPlayer by setting("SPacketSpawnPlayer", true, visibility = { page == Page.SERVER && categorySetting == CategorySlider.ENTITY })

    private val fileTimeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss_SSS")

    private var start = 0L
    private var last = 0L
    private var lastTick = 0L
    private val timer = TickTimer(TimeUnit.SECONDS)

    private var filename = ""
    private var lines = ArrayList<String>()

    private enum class Page {
        GENERAL, CLIENT, SERVER
    }
    enum class CategorySlider {
        PLAYER, INVENTORY, SYSTEM, WORLD, ENTITY
    }

    private enum class PacketSide(override val displayName: String) : DisplayEnum {
        CLIENT("Client"),
        SERVER("Server"),
        BOTH("Both")
    }

    enum class LogMode(override val displayName: String) : DisplayEnum {
        CHAT("Chat"),
        FILE("File"),
        CHAT_AND_FILE("Chat+File"),
        ONLY_HUD("Only HUD"),
        ALL("All")
    }

    private enum class CaptureTiming {
        PRE, POST
    }

    init {
        onEnable {
            PacketLogViewer.clear()
            start = System.currentTimeMillis()
            filename = "${fileTimeFormatter.format(LocalTime.now())}.csv"

            synchronized(this) {
                lines.add("From,Packet Name,Time Since Start (ms),Time Since Last (ms),Data\n")
            }
        }

        onDisable {
            write()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (showClientTicks) {
                synchronized(this@PacketLogger) {
                    val current = System.currentTimeMillis()
                    val line = "Tick Pulse: Start Delta: ${current - start}, Last Tick Delta: ${current - lastTick}\n"
                    if (logMode == LogMode.CHAT_AND_FILE || logMode == LogMode.FILE || logMode == LogMode.ALL) {
                        lines.add(line)
                    }
                    if (logMode == LogMode.CHAT_AND_FILE || logMode == LogMode.CHAT || logMode == LogMode.ALL) {
                        MessageSendHelper.sendChatMessage(line)
                    }
                    if (logMode == LogMode.ONLY_HUD || logMode == LogMode.ALL) {
                        if (PacketLogViewer.visible) {
                            PacketLogViewer.addPacketLog(line.replace("\n", ""))
                        }
                    }
                    lastTick = current
                }
            }

            /* Don't let lines get too big, write periodically to the file */
            if (lines.size >= 500) {
                write()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
            PacketLogViewer.clear()
        }

        listener<PacketEvent.Receive>(Int.MAX_VALUE) {
            if (captureTiming != CaptureTiming.PRE || ignoreCancelled && it.cancelled) return@listener
            if (mc.isIntegratedServerRunning && it.packet.javaClass.name.startsWith("net.minecraft.network.play.client")) return@listener

            receivePacket(it.packet)
        }

        listener<PacketEvent.PostReceive>(Int.MIN_VALUE) {
            if (captureTiming != CaptureTiming.POST || ignoreCancelled && it.cancelled) return@listener
            if (mc.isIntegratedServerRunning && it.packet.javaClass.name.startsWith("net.minecraft.network.play.client")) return@listener

            receivePacket(it.packet)
        }

        listener<PacketEvent.Send>(Int.MAX_VALUE) {
            if (captureTiming != CaptureTiming.PRE || ignoreCancelled && it.cancelled) return@listener
            if (mc.isIntegratedServerRunning && it.packet.javaClass.name.startsWith("net.minecraft.network.play.server")) return@listener
            sendPacket(it.packet)
        }

        listener<PacketEvent.PostSend>(Int.MIN_VALUE) {
            if (captureTiming != CaptureTiming.POST || ignoreCancelled && it.cancelled) return@listener
            if (mc.isIntegratedServerRunning && it.packet.javaClass.name.startsWith("net.minecraft.network.play.server")) return@listener

            sendPacket(it.packet)
        }
    }

    private fun sendPacket(packet: Packet<*>) {
        if (packetSide == PacketSide.CLIENT || packetSide == PacketSide.BOTH) {
            when (packet) {
                is CPacketAnimation -> {
                    if (!cPacketAnimation) return
                    logClient(packet) {
                        "hand" to packet.hand
                    }
                }
                is CPacketChatMessage -> {
                    if (!cPacketChatMessage) return
                    logClient(packet) {
                        "message" to packet.message
                    }
                }
                is CPacketClickWindow -> {
                    if (!cPacketClickWindow) return
                    logClient(packet) {
                        "windowId" to packet.windowId
                        "slotID" to packet.slotId
                        "mouseButton" to packet.usedButton
                        "clickType" to packet.clickType
                        "transactionID" to packet.actionNumber
                        "clickedItem" to packet.clickedItem
                    }
                }
                is CPacketClientSettings -> {
                    if (!cPacketClientSettings) return
                    logClient(packet) {
                        "lang" to packet.lang
                        "view" to packet.view
                        "chatVisibility" to packet.chatVisibility
                        "enableColors" to packet.isColorsEnabled
                        "modelPartFlags" to packet.modelPartFlags
                        "mainHand" to packet.mainHand.name
                    }
                }
                is CPacketClientStatus -> {
                    if (!cPacketClientStatus) return
                    logClient(packet) {
                        "action" to packet.status.name
                    }
                }
                is CPacketCloseWindow -> {
                    logClient(packet) {
                        "windowID" to packet.windowID
                    }
                }
                is CPacketConfirmTeleport -> {
                    logClient(packet) {
                        "teleportID" to packet.teleportId
                    }
                }
                is CPacketConfirmTransaction -> {
                    if (!cPacketConfirmTransaction) return
                    logClient(packet) {
                        "windowID" to packet.windowId
                        "actionNumber" to packet.uid
                        "accepted" to packet.accepted
                    }
                }
                is CPacketCreativeInventoryAction -> {
                    if (!cPacketCreativeInventoryAction) return
                    logClient(packet) {
                        "slotID" to packet.slotId
                        "clickedItem" to packet.stack
                    }
                }
                is CPacketCustomPayload -> {
                    if (!cPacketCustomPayload) return
                    logClient(packet) {
                        "channel" to packet.channelName
                        "data" to packet.bufferData
                    }
                }
                is CPacketEnchantItem -> {
                    if (!cPacketEnchantItem) return
                    logClient(packet) {
                        "windowID" to packet.windowId
                        "button" to packet.button
                    }
                }
                is CPacketEntityAction -> {
                    if (!cPacketEntityAction) return
                    logClient(packet) {
                        "action" to packet.action.name
                        "auxData" to packet.auxData
                    }
                }
                is CPacketHeldItemChange -> {
                    if (!cPacketHeldItemChange) return
                    logClient(packet) {
                        "slotID" to packet.slotId
                    }
                }
                is CPacketInput -> {
                    if (!cPacketInput) return
                    logClient(packet) {
                        "forward" to packet.forwardSpeed
                        "strafe" to packet.strafeSpeed
                        "jump" to packet.isJumping
                        "sneak" to packet.isSneaking
                    }
                }
                is CPacketKeepAlive -> {
                    if (!cPacketKeepAlive) return
                    logClient(packet) {
                        "key" to packet.key
                    }
                }
                is CPacketPlaceRecipe -> {
                    if (!cPacketPlaceRecipe) return
                    logClient(packet) {
                        "windowID" to packet.func_194318_a()
                        @Suppress("DEPRECATION")
                        "recipe" to CraftingManager.getIDForRecipe(packet.func_194317_b())
                        "shift" to packet.func_194319_c()
                    }
                }

                is CPacketPlayer.Rotation -> {
                    if (!cPacketPlayerRotation) return
                    logClient(packet) {
                        "yaw" to packet.playerYaw
                        "pitch" to packet.playerPitch
                        "onGround" to packet.isOnGround
                    }
                }
                is CPacketPlayer.Position -> {
                    if (!cPacketPlayerPosition) return
                    logClient(packet) {
                        "x" to packet.playerX
                        "y" to packet.playerY
                        "z" to packet.playerZ
                        "onGround" to packet.isOnGround
                    }
                }
                is CPacketPlayer.PositionRotation -> {
                    if (!cPacketPlayerPositionRotation) return
                    logClient(packet) {
                        "x" to packet.playerX
                        "y" to packet.playerY
                        "z" to packet.playerZ
                        "yaw" to packet.playerYaw
                        "pitch" to packet.playerPitch
                        "onGround" to packet.isOnGround
                    }
                }
                is CPacketPlayer -> {
                    if (!cPacketPlayer) return
                    logClient(packet) {
                        "onGround" to packet.isOnGround
                    }
                }
                is CPacketPlayerAbilities -> {
                    if (!cPacketPlayerAbilities) return
                    logClient(packet) {
                        "invulnerable" to packet.isInvulnerable
                        "flying" to packet.isFlying
                        "allowFlying" to packet.isAllowFlying
                        "creativeMode" to packet.isCreativeMode
                        "flySpeed" to packet.flySpeed
                        "walkSpeed" to packet.walkSpeed
                    }
                }
                is CPacketPlayerDigging -> {
                    if (!cPacketPlayerDigging) return
                    logClient(packet) {
                        "pos" to packet.position
                        "facing" to packet.facing.name
                        "action" to packet.action.name
                    }
                }
                is CPacketPlayerTryUseItem -> {
                    if (!cPacketPlayerTryUseItem) return
                    logClient(packet) {
                        "hand" to packet.hand
                    }
                }
                is CPacketPlayerTryUseItemOnBlock -> {
                    if (!cPacketPlayerTryUseItemOnBlock) return
                    logClient(packet) {
                        "pos" to packet.pos
                        "side" to packet.direction.name
                        "hitVecX" to packet.facingX
                        "hitVecY" to packet.facingY
                        "hitVecZ" to packet.facingZ
                    }
                }
                is CPacketRecipeInfo -> {
                    if (!cPacketRecipeInfo) return
                    logClient(packet) {
                        "purpose" to packet.purpose.name
                        @Suppress("DEPRECATION")
                        "recipe" to CraftingManager.getIDForRecipe(packet.recipe)
                        "guiOpen" to packet.isGuiOpen
                        "filteringCraftable" to packet.isFilteringCraftable
                    }
                }
                is CPacketResourcePackStatus -> {
                    if (!cPacketResourcePackStatus) return
                    logClient(packet) {
                        "action" to packet.action.name
                    }
                }
                is CPacketSeenAdvancements -> {
                    if (!cPacketSeenAdvancements) return
                    logClient(packet) {
                        "action" to packet.action.name
                        @Suppress("UNNECESSARY_SAFE_CALL")
                        packet.tab?.let {
                            "tabName" to it.namespace
                            "tabPath" to it.path
                        }
                    }
                }
                is CPacketSpectate -> {
                    if (!cPacketSpectate) return
                    logClient(packet) {
                        "uuid" to packet.uuid
                    }
                }
                is CPacketSteerBoat -> {
                    if (!cPacketSteerBoat) return
                    logClient(packet) {
                        "left" to packet.left
                        "right" to packet.right
                    }
                }
                is CPacketTabComplete -> {
                    if (!cPacketTabComplete) return
                    logClient(packet) {
                        "text" to packet.message
                        "hasTarget" to packet.hasTargetBlock()
                        packet.targetBlock?.let {
                            "targetBlockPos" to it
                        }
                    }
                }
                is CPacketUpdateSign -> {
                    if (!cPacketUpdateSign) return
                    logClient(packet) {
                        "x" to packet.position.x
                        "y" to packet.position.y
                        "z" to packet.position.z
                        "line1" to packet.lines[0]
                        "line2" to packet.lines[1]
                        "line3" to packet.lines[2]
                        "line4" to packet.lines[3]
                    }
                }
                is CPacketUseEntity -> {
                    if (!cPacketUseEntity) return
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    logClient(packet) {
                        "entityId" to packet.useEntityId
                        "action" to packet.action.name
                        "hitVecX" to packet.hitVec?.x
                        "hitVecX" to packet.hitVec?.y
                        "hitVecX" to packet.hitVec?.z
                        "hand" to packet.hand?.name
                    }
                }
                is CPacketVehicleMove -> {
                    if (!cPacketVehicleMove) return
                    logClient(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                else -> {
                    logClient(packet) {
                        +"Not Registered in PacketLogger"
                    }
                }
            }
        }
    }

    private fun receivePacket(packet: Packet<*>) {
        if (packetSide == PacketSide.SERVER || packetSide == PacketSide.BOTH) {
            when (packet) {
                is SPacketAdvancementInfo -> {
                    if (!sPacketAdvancementInfo) return
                    logServer(packet) {
                        "isFirstSync" to packet.isFirstSync
                        "advancementsToAdd" to buildString {
                            for (entry in packet.advancementsToAdd) {
                                append("> ")

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                        "advancementsToRemove" to buildString {
                            for (entry in packet.advancementsToRemove) {
                                append("> path: ")
                                append(entry.path)
                                append(", namespace:")
                                append(entry.namespace)
                                append(' ')
                            }
                        }
                        "progressUpdates" to buildString {
                            for (entry in packet.progressUpdates) {
                                append("> ")

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketAnimation -> {
                    if (!sPacketAnimation) return
                    logServer(packet) {
                        "entityId" to packet.entityID
                        "animationType" to packet.animationType
                    }
                }
                is SPacketBlockAction -> {
                    if (!sPacketBlockAction) return
                    logServer(packet) {
                        "blockPosition" to packet.blockPosition
                        "instrument" to packet.data1
                        "pitch" to packet.data2
                        "blockType" to packet.blockType
                    }
                }
                is SPacketBlockBreakAnim -> {
                    if (!sPacketBlockBreakAnim) return
                    logServer(packet) {
                        "breakerId" to packet.breakerId
                        "position" to packet.position
                        "progress" to packet.progress
                    }
                }
                is SPacketBlockChange -> {
                    if (!sPacketBlockChange) return
                    logServer(packet) {
                        "blockPosition" to packet.blockPosition
                        "block" to packet.blockState.block.localizedName
                    }
                }
                is SPacketCamera -> {
                    if (!sPacketCamera) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                    }
                }
                is SPacketChangeGameState -> {
                    if (!sPacketChangeGameState) return
                    logServer(packet) {
                        "value" to packet.value
                        "gameState" to packet.gameState
                    }
                }
                is SPacketChat -> {
                    if (!sPacketChat) return
                    logServer(packet) {
                        "unformattedText" to packet.chatComponent.unformattedText
                        "type" to packet.type
                        "itSystem" to packet.isSystem
                    }
                }
                is SPacketChunkData -> {
                    if (!sPacketChunkData) return
                    logServer(packet) {
                        "chunkX" to packet.chunkX
                        "chunkZ" to packet.chunkZ
                        "extractedSize" to packet.extractedSize
                    }
                }
                is SPacketCloseWindow -> {
                    if (!sPacketCloseWindow) return
                    logServer(packet) {
                        "windowId" to packet.windowId
                    }
                }
                is SPacketCollectItem -> {
                    if (!sPacketCollectItem) return
                    logServer(packet) {
                        "amount" to packet.amount
                        "collectedItemEntityID" to packet.collectedItemEntityID
                        "entityID" to packet.entityID
                    }
                }
                is SPacketCombatEvent -> {
                    if (!sPacketCombatEvent) return
                    logServer(packet) {
                        "eventType" to packet.eventType.name
                        "playerId" to packet.playerId
                        "entityId" to packet.entityId
                        "duration" to packet.duration
                        "deathMessage" to packet.deathMessage.unformattedText
                    }
                }
                is SPacketConfirmTransaction -> {
                    if (!sPacketConfirmTransaction) return
                    logServer(packet) {
                        "windowId" to packet.windowId
                        "transactionID" to packet.actionNumber
                        "accepted" to packet.wasAccepted()
                    }
                }
                is SPacketCooldown -> {
                    if (!sPacketCooldown) return
                    logServer(packet) {
                        "item" to packet.item.registryName
                        "ticks" to packet.ticks
                    }
                }
                is SPacketCustomPayload -> {
                    if (!sPacketCustomPayload) return
                    logServer(packet) {
                        "channelName" to packet.channelName
                        "bufferData" to packet.bufferData
                    }
                }
                is SPacketCustomSound -> {
                    if (!sPacketCustomSound) return
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "pitch" to packet.pitch
                        "category" to packet.category.name
                        "soundName" to packet.soundName
                        "volume" to packet.volume
                    }
                }
                is SPacketDestroyEntities -> {
                    if (!sPacketDestroyEntities) return
                    logServer(packet) {
                        "entityIDs" to buildString {
                            for (entry in packet.entityIDs) {
                                append("> ")
                                append(entry)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketDisconnect -> {
                    if (!sPacketDisconnect) return
                    logServer(packet) {
                        "reason" to packet.reason.unformattedText
                    }
                }
                is SPacketDisplayObjective -> {
                    if (!sPacketDisplayObjective) return
                    logServer(packet) {
                        "position" to packet.position
                        "name" to packet.name
                    }
                }
                is SPacketEffect -> {
                    if (!sPacketEffect) return
                    logServer(packet) {
                        "soundData" to packet.soundData
                        "soundPos" to packet.soundPos
                        "soundType" to packet.soundType
                        "isSoundServerwide" to packet.isSoundServerwide
                    }
                }
                is SPacketEntity.S15PacketEntityRelMove -> {
                    if (!s15PacketEntityRelMove) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity.S16PacketEntityLook -> {
                    if (!s16PacketEntityLook) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity.S17PacketEntityLookMove -> {
                    if(!s17PacketEntityLookMove) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity -> {
                    if (!sPacketEntity) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntityAttach -> {
                    if (!sPacketEntityAttach) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "vehicleEntityId" to packet.vehicleEntityId
                    }
                }
                is SPacketEntityEffect -> {
                    if (!sPacketEntityEffect) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "duration" to packet.duration
                        "amplifier" to packet.amplifier
                        "effectId" to packet.effectId
                        "isAmbient" to packet.isAmbient
                        "isMaxDuration" to packet.isMaxDuration
                    }
                }
                is SPacketEntityEquipment -> {
                    if (!sPacketEntityEquipment) return
                    logServer(packet) {
                        "entityId" to packet.entityID
                        "slot" to packet.equipmentSlot.name
                        "stack" to packet.itemStack.displayName
                    }
                }
                is SPacketEntityHeadLook -> {
                    if (!sPacketEntityHeadLook) return
                    logServer(packet) {
                        "entityId" to packet.entityHeadLookEntityId
                        "yaw" to packet.yaw
                    }
                }
                is SPacketEntityMetadata -> {
                    if (!sPacketEntityMetadata) return
                    logServer(packet) {
                        "dataEntries" to buildString {
                            for (entry in packet.dataManagerEntries) {
                                append("> isDirty: ")
                                append(entry.isDirty)

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketEntityProperties -> {
                    if (!sPacketEntityProperties) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "snapshots" to packet.snapshots
                    }
                }
                is SPacketEntityStatus -> {
                    if (!sPacketEntityStatus) return
                    logServer(packet) {
                        "opCode" to packet.opCode
                    }
                }
                is SPacketEntityTeleport -> {
                    if (!sPacketEntityTeleport) return
                    logServer(packet) {
                        "entityID" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                is SPacketEntityVelocity -> {
                    if (!sPacketEntityVelocity) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "motionX" to packet.motionX
                        "motionY" to packet.motionY
                        "motionZ" to packet.motionZ
                    }
                }
                is SPacketExplosion -> {
                    if (!sPacketExplosion) return
                    logServer(packet) {
                        "strength" to packet.strength
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "motionX" to packet.motionX
                        "motionY" to packet.motionY
                        "motionZ" to packet.motionZ
                        "affectedBlockPositions" to buildString {
                            for (block in packet.affectedBlockPositions) {
                                append("> x: ")
                                append(block.x)

                                append("y: ")
                                append(block.y)

                                append("z: ")
                                append(block.z)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketHeldItemChange -> {
                    if (!sPacketHeldItemChange) return
                    logServer(packet) {
                        "heldItemHotbarIndex" to packet.heldItemHotbarIndex
                    }
                }
                is SPacketJoinGame -> {
                    if (!sPacketJoinGame) return
                    logServer(packet) {
                        "playerId" to packet.playerId
                        "difficulty" to packet.difficulty.name
                        "dimension" to packet.dimension
                        "gameType" to packet.gameType.name
                        "isHardcoreMode" to packet.isHardcoreMode
                        "isReducedDebugInfo" to packet.isReducedDebugInfo
                        "maxPlayers" to packet.maxPlayers
                        "worldType" to packet.worldType
                    }
                }
                is SPacketKeepAlive -> {
                    if (!sPacketKeepAlive) return
                    logServer(packet) {
                        "id" to packet.id
                    }
                }
                is SPacketMaps -> {
                    if (!sPacketMaps) return
                    logServer(packet) {
                        "mapId" to packet.mapId
                        "mapScale" to packet.mapScale
                        "trackingPosition" to packet.trackingPosition
                        "icons" to packet.icons
                        "minX" to packet.minX
                        "minZ" to packet.minZ
                        "columns" to packet.columns
                        "rows" to packet.rows
                        "data" to packet.mapDataBytes
                    }
                }
                is SPacketMoveVehicle -> {
                    if (!sPacketMoveVehicle) return
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                is SPacketMultiBlockChange -> {
                    if (!sPacketMultiBlockChange) return
                    logServer(packet) {
                        "changedBlocks" to buildString {
                            for (changedBlock in packet.changedBlocks) {
                                append("> x: ")
                                append(changedBlock.pos.x)

                                append("y: ")
                                append(changedBlock.pos.y)

                                append("z: ")
                                append(changedBlock.pos.z)

                                append("offset: ")
                                append(changedBlock.offset)

                                append("blockState: ")
                                @Suppress("DEPRECATION")
                                append(Block.BLOCK_STATE_IDS.get(changedBlock.blockState))

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketOpenWindow -> {
                    if (!sPacketOpenWindow) return
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "windowTitle" to packet.windowTitle
                        "guiId" to packet.guiId
                        "windowId" to packet.windowId
                        "slotCount" to packet.slotCount
                    }
                }
                is SPacketParticles -> {
                    if (!sPacketParticles) return
                    logServer(packet) {
                        "particleType" to packet.particleType.name
                        "isLongDistance" to packet.isLongDistance
                        "particleType" to packet.particleType.name
                        "particleCount" to packet.particleCount
                        "particleSpeed" to packet.particleSpeed
                        "xCoordinate" to packet.xCoordinate
                        "yCoordinate" to packet.yCoordinate
                        "zCoordinate" to packet.zCoordinate
                        "xOffset" to packet.xOffset
                        "yOffset" to packet.yOffset
                        "zOffset" to packet.zOffset
                        "particleArgs" to packet.particleArgs
                    }
                }
                is SPacketPlaceGhostRecipe -> {
                    if (!sPacketPlaceGhostRecipe) return
                    logServer(packet) {
                        "windowId" to packet.func_194313_b()
                        @Suppress("DEPRECATION")
                        "recipeId" to CraftingManager.getIDForRecipe(packet.func_194311_a())
                    }
                }
                is SPacketPlayerAbilities -> {
                    if (!sPacketPlayerAbilities) return
                    logServer(packet) {
                        "isInvulnerable" to packet.isInvulnerable
                        "isFlying" to packet.isFlying
                        "allowFlying" to packet.isAllowFlying
                        "isCreativeMode" to packet.isCreativeMode
                        "flySpeed" to packet.flySpeed
                        "walkSpeed" to packet.walkSpeed
                    }
                }
                is SPacketPlayerListHeaderFooter -> {
                    if (!sPacketPlayerListHeaderFooter) return
                    logServer(packet) {
                        "header" to ITextComponent.Serializer.componentToJson(packet.header)
                        "footer" to ITextComponent.Serializer.componentToJson(packet.footer)
                    }
                }
                is SPacketPlayerListItem -> {
                    if (!sPacketPlayerListItem) return
                    logServer(packet) {
                        "action" to packet.action.name
                        "entries" to buildString {
                            @Suppress("UNNECESSARY_SAFE_CALL")
                            for (entry in packet.entries) {
                                append("> displayName: ")
                                append(entry.displayName)
                                append(" gameMode: ")
                                append(entry.gameMode?.name)
                                append(" ping: ")
                                append(entry.ping)
                                append(" profile.id: ")
                                append(entry.profile?.id)
                                append(" profile.name: ")
                                append(entry.profile?.name)
                                append(" profile.properties: ")
                                append(entry.profile?.properties)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketPlayerPosLook -> {
                    if (!sPacketPlayerPosLook) return
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "teleportID" to packet.teleportId
                        "flags" to buildString {
                            for (entry in packet.flags) {
                                append("> ")
                                append(entry.name)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketRecipeBook -> {
                    if (!sPacketRecipeBook) return
                    @Suppress("DEPRECATION")
                    logServer(packet) {
                        "state" to packet.state.name
                        "recipes" to buildString {
                            for (recipe in packet.recipes) {
                                append("> ")
                                append(CraftingManager.getIDForRecipe(recipe))
                                append(' ')
                            }
                        }
                        "displayedRecipes" to buildString {
                            for (recipe in packet.displayedRecipes) {
                                append("> ")
                                append(CraftingManager.getIDForRecipe(recipe))
                                append(' ')
                            }
                        }
                        "guiOpen" to packet.isGuiOpen
                        "filteringCraftable" to packet.isFilteringCraftable
                    }
                }
                is SPacketRemoveEntityEffect -> {
                    if (!sPacketRemoveEntityEffect) return
                    logServer(packet) {
                        mc.world?.let { world ->
                            packet.getEntity(world)?.let {
                                "entityID" to it.entityId
                            }
                        }
                        packet.potion?.let { "effectID" to Potion.getIdFromPotion(it) }
                    }
                }
                is SPacketResourcePackSend -> {
                    if (!sPacketResourcePackSend) return
                    logServer(packet) {
                        "url" to packet.url
                        "hash" to packet.hash
                    }
                }
                is SPacketRespawn -> {
                    if (!sPacketRespawn) return
                    logServer(packet) {
                        "dimensionID" to packet.dimensionID
                        "difficulty" to packet.difficulty.name
                        "gameType" to packet.gameType.name
                        "worldType" to packet.worldType.name
                    }
                }
                is SPacketScoreboardObjective -> {
                    if (!sPacketScoreboardObjective) return
                    logServer(packet) {
                        "name" to packet.objectiveName
                        "value" to packet.objectiveValue
                        "type" to packet.renderType.renderType
                        "action" to packet.action
                    }
                }
                is SPacketSelectAdvancementsTab -> {
                    if (!sPacketSelectAdvancementsTab) return
                    logServer(packet) {
                        packet.tab?.let {
                            "name" to it.namespace
                            "path" to it.path
                        }?.run {
                            "name" to ""
                            "path" to ""
                        }
                    }
                }
                is SPacketServerDifficulty -> {
                    if (!sPacketServerDifficulty) return
                    logServer(packet) {
                        "difficulty" to packet.difficulty.name
                        "difficultyLocked" to packet.isDifficultyLocked
                    }
                }
                is SPacketSetExperience -> {
                    if (!sPacketSetExperience) return
                    logServer(packet) {
                        "experienceBar" to packet.experienceBar
                        "totalExperience" to packet.totalExperience
                        "level" to packet.level
                    }
                }
                is SPacketSetPassengers -> {
                    if (!sPacketSetPassengers) return
                    logServer(packet) {
                        "entityID" to packet.entityId
                        "passengers" to buildString {
                            for (passenger in packet.passengerIds) {
                                append("> ")
                                append(passenger)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketSetSlot -> {
                    if (!sPacketSetSlot) return
                    logServer(packet) {
                        "slot" to packet.slot
                        "stack" to packet.stack.displayName
                        "windowId" to packet.windowId
                    }
                }
                is SPacketSignEditorOpen -> {
                    if (!sPacketSignEditorOpen) return
                    logServer(packet) {
                        "posX" to packet.signPosition.x
                        "posY" to packet.signPosition.y
                        "posZ" to packet.signPosition.z
                    }
                }
                is SPacketSoundEffect -> {
                    if (!sPacketSoundEffect) return
                    logServer(packet) {
                        "sound" to packet.sound.soundName
                        "category" to packet.category
                        "posX" to packet.x
                        "posY" to packet.y
                        "posZ" to packet.z
                        "volume" to packet.volume
                        "pitch" to packet.pitch
                    }
                }
                is SPacketSpawnExperienceOrb -> {
                    if (!sPacketSpawnExperienceOrb) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "count" to packet.xpValue
                    }
                }
                is SPacketSpawnGlobalEntity -> {
                    if (!sPacketSpawnGlobalEntity) return
                    logServer(packet) {
                        "entityID" to packet.entityId
                        "type" to packet.type
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                    }
                }
                is SPacketSpawnMob -> {
                    if (!sPacketSpawnMob) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "uuid" to packet.uniqueId
                        "type" to packet.entityType
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "headPitch" to packet.headPitch
                        "velocityX" to packet.velocityX
                        "velocityY" to packet.velocityY
                        "velocityZ" to packet.velocityZ
                        packet.dataManagerEntries?.let { metadata ->
                            "metadata" to buildString {
                                for (entry in metadata) {
                                    append("> ")
                                    append(entry.key.id)
                                    append(": ")
                                    append(entry.value)
                                    append(' ')
                                }
                            }
                        }
                    }
                }
                is SPacketSpawnObject -> {
                    if (!sPacketSpawnObject) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "uuid" to packet.uniqueId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "speedX" to packet.speedX
                        "speedY" to packet.speedY
                        "speedZ" to packet.speedZ
                        "pitch" to packet.pitch
                        "yaw" to packet.yaw
                        "type" to packet.type
                        "data" to packet.data
                    }
                }
                is SPacketSpawnPainting -> {
                    if (!sPacketSpawnPainting) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "uuid" to packet.uniqueId
                        "title" to packet.title
                        "x" to packet.position.x
                        "y" to packet.position.y
                        "z" to packet.position.z
                        "facing" to packet.facing.name
                    }
                }
                is SPacketSpawnPlayer -> {
                    if (!sPacketSpawnPlayer) return
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "uniqueID" to packet.uniqueId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "dataManagerEntries" to buildString {
                            packet.dataManagerEntries?.forEach {
                                append("> ")
                                append(it.key)
                                append(": ")
                                append(it.value)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketSpawnPosition -> {
                    if (!sPacketSpawnPosition) return
                    logServer(packet) {
                        "pos" to packet.spawnPos
                    }
                }
                is SPacketStatistics -> {
                    if (!sPacketStatistics) return
                    logServer(packet) {
                        "statistics" to packet.statisticMap
                    }
                }
                is SPacketTabComplete -> {
                    if (!sPacketTabComplete) return
                    logServer(packet) {
                        "matches" to packet.matches
                    }
                }
                is SPacketTeams -> {
                    if (!sPacketTeams) return
                    logServer(packet) {
                        "action" to packet.action
                        "type" to packet.displayName
                        "itSystem" to packet.color
                    }
                }
                is SPacketTimeUpdate -> {
                    if (!sPacketTimeUpdate) return
                    logServer(packet) {
                        "totalWorldTime" to packet.totalWorldTime
                        "worldTime" to packet.worldTime
                    }
                }
                is SPacketTitle -> {
                    if (!sPacketTitle) return
                    logServer(packet) {
                        "action" to packet.type
                        "text" to packet.message
                        "fadeIn" to packet.fadeInTime
                        "stay" to packet.displayTime
                        "fadeOut" to packet.fadeOutTime
                    }
                }
                is SPacketUnloadChunk -> {
                    if (!sPacketUnloadChunk) return
                    logServer(packet) {
                        "x" to packet.x
                        "z" to packet.z
                    }
                }
                is SPacketUpdateBossInfo -> {
                    if (!sPacketUpdateBossInfo) return
                    logServer(packet) {
                        "uuid" to packet.uniqueId
                        "operation" to packet.operation.name
                        "name" to ITextComponent.Serializer.componentToJson(packet.name)
                        "percent" to packet.percent
                        "color" to packet.color
                        "overlay" to packet.overlay.name
                        "darkenSky" to packet.shouldDarkenSky()
                        "playEndBossMusic" to packet.shouldPlayEndBossMusic()
                        "createFog" to packet.shouldCreateFog()
                    }
                }
                is SPacketUpdateHealth -> {
                    if (!sPacketUpdateHealth) return
                    logServer(packet) {
                        "health" to packet.health
                        "foodLevel" to packet.foodLevel
                        "saturationLevel" to packet.saturationLevel
                    }
                }
                is SPacketUpdateScore -> {
                    if (!sPacketUpdateScore) return
                    logServer(packet) {
                        "playerName" to packet.playerName
                        "objective" to packet.objectiveName
                        "value" to packet.scoreValue
                        "action" to packet.scoreAction.name
                    }
                }
                is SPacketUpdateTileEntity -> {
                    if (!sPacketUpdateTileEntity) return
                    logServer(packet) {
                        "pos" to packet.pos
                        "type" to packet.tileEntityType
                        "nbt" to packet.nbtCompound.toString()
                    }
                }
                is SPacketUseBed -> {
                    if (!sPacketUseBed) return
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    logServer(packet) {
                        mc.world?.let { world ->
                            "player" to packet.getPlayer(world)?.name
                        }
                        "pos" to packet.bedPosition
                    }
                }
                is SPacketWindowItems -> {
                    if (!sPacketWindowItems) return
                    logServer(packet) {
                        "windowId" to packet.windowId
                        "itemStacks" to buildString {
                            for (entry in packet.itemStacks) {
                                append("> ")
                                append(entry.displayName)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketWindowProperty -> {
                    if (!sPacketWindowProperty) return
                    logServer(packet) {
                        "windowId" to packet.windowId
                        "property" to packet.property
                        "value" to packet.value
                    }
                }
                is SPacketWorldBorder -> {
                    if (!sPacketWorldBorder) return
                    logServer(packet) {
                        "action" to packet.action.name
                        "size" to packet.size
                        "centerX" to packet.centerX
                        "centerZ" to packet.centerZ
                        "targetSize" to packet.targetSize
                        "diameter" to packet.diameter
                        "timeUntilTarget" to packet.timeUntilTarget
                        "warningTime" to packet.warningTime
                        "warningDistance" to packet.warningDistance
                    }
                }
                else -> {
                    logServer(packet) {
                        +"Not Registered in PacketLogger"
                    }
                }
            }
        }
    }


    private fun write() {
        if (logMode != LogMode.FILE && logMode != LogMode.CHAT_AND_FILE && logMode != LogMode.ALL) {
            lines.clear()
            return
        }

        val lines = synchronized(this) {
            val cache = lines
            lines = ArrayList()
            cache
        }

        defaultScope.launch(Dispatchers.IO) {
            try {
                with(File(FolderUtils.packetLogFolder)) {
                    if (!exists()) mkdir()
                }

                FileWriter("${FolderUtils.packetLogFolder}${filename}", true).buffered().use {
                    for (line in lines) it.write(line)
                }
                runSafe {
                    MessageSendHelper.sendChatMessage("$chatName Log saved at ${TextFormatting.GREEN}${FolderUtils.packetLogFolder}${filename}")
                }
            } catch (e: Exception) {
                LambdaMod.LOG.warn("$chatName Failed saving packet log!", e)
            }
        }
    }

    private inline fun logClient(packet: Packet<*>, block: PacketLogBuilder.() -> Unit) {
        PacketLogBuilder(PacketSide.CLIENT, packet).apply(block).build()
    }

    private inline fun logServer(packet: Packet<*>, block: PacketLogBuilder.() -> Unit) {
        PacketLogBuilder(PacketSide.SERVER, packet).apply(block).build()
    }

    private class PacketLogBuilder(val side: PacketSide, val packet: Packet<*>) {
        private val stringBuilder = StringBuilder()

        init {
            stringBuilder.apply {
                append(side.displayName)
                append(',')

                append(packet.javaClass.simpleName)
                if (absoluteTime) {
                    append(',')
                    append(System.currentTimeMillis())
                }
                if (startDelta) {
                    append(',')
                    append(System.currentTimeMillis() - start)
                }
                if (lastDelta) {
                    append(',')
                    append(System.currentTimeMillis() - last)
                }
                append(": ")
            }
        }

        operator fun String.unaryPlus() {
            stringBuilder.append(this)
        }

        infix fun String.to(value: Any?) {
            if (value != null) {
                add(this, value.toString())
            }
        }

        infix fun String.to(value: String?) {
            if (value != null) {
                add(this, value)
            }
        }

        infix fun String.to(value: BlockPos?) {
            if (value != null) {
                add("x", value.x.toString())
                add("y", value.y.toString())
                add("z", value.z.toString())
            }
        }

        fun add(key: String, value: String) {
            stringBuilder.apply {
                append(key)
                append(": ")
                append(value)
                append(' ')
            }
        }

        fun build() {
            val string = stringBuilder.run {
                append('\n')
                toString()
            }

            if (logMode == LogMode.CHAT_AND_FILE || logMode == LogMode.FILE || logMode == LogMode.ALL) {
                synchronized(PacketLogger) {
                    lines.add(string)
                    last = System.currentTimeMillis()
                }
            }

            if (logMode == LogMode.CHAT_AND_FILE || logMode == LogMode.CHAT || logMode == LogMode.ALL) {
                MessageSendHelper.sendChatMessage(string)
            }

            if (logMode == LogMode.ONLY_HUD || logMode == LogMode.ALL) {
                if (PacketLogViewer.visible) {
                    PacketLogViewer.addPacketLog(string.replace("\n", ""))
                }
            }
        }
    }
}
