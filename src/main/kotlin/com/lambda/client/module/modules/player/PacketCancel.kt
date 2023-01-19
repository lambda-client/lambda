package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import net.minecraft.network.login.client.CPacketEncryptionResponse
import net.minecraft.network.login.client.CPacketLoginStart
import net.minecraft.network.login.server.SPacketEnableCompression
import net.minecraft.network.login.server.SPacketEncryptionRequest
import net.minecraft.network.login.server.SPacketLoginSuccess
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.network.status.client.CPacketPing
import net.minecraft.network.status.client.CPacketServerQuery
import net.minecraft.network.status.server.SPacketPong
import net.minecraft.network.status.server.SPacketServerInfo

object PacketCancel : Module(
    name = "PacketCancel",
    description = "Cancels specific packets for various interactions",
    category = Category.PLAYER
) {
    enum class Side {
        CLIENT, SERVER
    }

    enum class CategorySlider {
        PLAYER, INVENTORY, SYSTEM, WORLD, ENTITY
    }

    private val side by setting("Side", Side.CLIENT)

    private val categorySetting by setting("Category", CategorySlider.PLAYER)

    private val CPacketAnimationSetting by setting("CPacketAnimation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketUseEntitySetting by setting("CPacketUseEntity", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private val CPacketChatMessageSetting by setting("CPacketChatMessage", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketClickWindowSetting by setting("CPacketClickWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketClientSettingsSetting by setting("CPacketClient", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketClientStatusSetting by setting("CPacketClientStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketCloseWindowSetting by setting("CPacketCloseWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketConfirmTeleportSetting by setting("CPacketConfirmTeleport", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketConfirmTransactionSetting by setting("CPacketConfirmTransaction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketCreativeInventoryActionSetting by setting("CPacketCreativeInventoryAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketCustomPayloadSetting by setting("CPacketCustomPayload", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketEnchantItemSetting by setting("CPacketEnchantItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketEntityActionSetting by setting("CPacketEntityAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private val CPacketPlayerPositionSetting by setting("CPacketPlayerPosition", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketPlayerRotationSetting by setting("CPacketPlayerRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketPlayerPositionRotationSetting by setting("CPacketPlayerPositionRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketHeldItemChangeSetting by setting("CPacketHeldItemChange", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketInputSetting by setting("CPacketInput", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketPlaceRecipeSetting by setting("CPacketPlaceRecipe", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketPlayerAbilitiesSetting by setting("CPacketPlayerAbilities", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketPlayerTryUseItemSetting by setting("CPacketPlayerTryUseItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketPlayerTryUseItemOnBlockSetting by setting("CPacketPlayerTryUseItemOnBlock", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketServerQuerySetting by setting("CPacketServerQuery", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketLoginStartSetting by setting("CPacketLoginStart", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketPingSetting by setting("CPacketPing", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketEncryptionResponseSetting by setting("CPacketEncryptionResponse", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketVehicleMoveSetting by setting("CPacketVehicleMove", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private val CPacketUpdateSignSetting by setting("CPacketUpdateSign", false, { side == Side.CLIENT && categorySetting == CategorySlider.WORLD })
    private val CPacketTabCompleteSetting by setting("CPacketTabComplete", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketSteerBoatSetting by setting("CPacketSteerBoat", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private val CPacketSpectateSetting by setting("CPacketSpectate", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketSeenAdvancementsSetting by setting("CPacketSeenAdvancements", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketResourcePackStatusSetting by setting("CPacketResourcePackStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private val CPacketRecipeInfoSetting by setting("CPacketRecipeInfo", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private val CPacketPlayerDiggingSetting by setting("CPacketPlayerDigging", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private val CPacketKeepAliveSetting by setting("CPacketKeepAlive", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })

    private val SPacketEntityS17PacketEntityLookMoveSetting by setting("SPacketEntity.S17PacketEntityLookMove", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityS16PacketEntityLookSetting by setting("SPacketEntity.S16PacketEntityLook", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityS15PacketEntityRelMoveSetting by setting("SPacketEntity.S15PacketEntityRelMove", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketServerInfoSetting by setting("SPacketServerInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketLoginSuccessSetting by setting("SPacketLoginSuccess", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketWorldBorderSetting by setting("SPacketWorldBorder", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketWindowPropertySetting by setting("SPacketWindowProperty", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketWindowItemsSetting by setting("SPacketWindowItems", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketPongSetting by setting("SPacketPong", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketEncryptionRequestSetting by setting("SPacketEncryptionRequest", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketEnableCompressionSetting by setting("SPacketEnableCompression", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketDisconnectSetting by setting("SPacketDisconnect", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketUseBedSetting by setting("SPacketUseBed", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketUpdateTileEntitySetting by setting("SPacketUpdateTileEntity", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketUpdateScoreSetting by setting("SPacketUpdateScore", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketUpdateHealthSetting by setting("SPacketUpdateHealth", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketUpdateBossInfoSetting by setting("SPacketUpdateBossInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketUnloadChunkSetting by setting("SPacketUnloadChunk", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketTitleSetting by setting("SPacketTitle", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketTimeUpdateSetting by setting("SPacketTimeUpdate", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketTeamsSetting by setting("SPacketTeams", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketTabCompleteSetting by setting("SPacketTabComplete", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketStatisticsSetting by setting("SPacketStatistics", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketSpawnPositionSetting by setting("SPacketSpawnPosition", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketSpawnPaintingSetting by setting("SPacketSpawnPainting", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketSpawnObjectSetting by setting("SPacketSpawnObject", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketSpawnPlayerSetting by setting("SPacketSpawnPlayer", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketSpawnMobSetting by setting("SPacketSpawnMob", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketSpawnGlobalEntitySetting by setting("SPacketSpawnGlobalEntity", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketSpawnExperienceOrbSetting by setting("SPacketSpawnExperienceOrb", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketSoundEffectSetting by setting("SPacketSoundEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketSignEditorOpenSetting by setting("SPacketSignEditorOpen", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketSetSlotSetting by setting("SPacketSetSlot", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketSetExperienceSetting by setting("SPacketSetExperience", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketServerDifficultySetting by setting("SPacketServerDifficulty", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketSelectAdvancementsTabSetting by setting("SPacketSelectAdvancementsTab", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketScoreboardObjectiveSetting by setting("SPacketScoreboardObjective", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketRespawnSetting by setting("SPacketRespawn", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketResourcePackSendSetting by setting("SPacketResourcePackSend", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketRemoveEntityEffectSetting by setting("SPacketRemoveEntityEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketRecipeBookSetting by setting("SPacketRecipeBook", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketPlayerListItemSetting by setting("SPacketPlayerListItem", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketPlayerListHeaderFooterSetting by setting("SPacketPlayerListHeaderFooter", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketPlayerAbilitiesSetting by setting("SPacketPlayerAbilities", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketPlaceGhostRecipeSetting by setting("SPacketPlaceGhostRecipe", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketParticlesSetting by setting("SPacketParticles", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketOpenWindowSetting by setting("SPacketOpenWindow", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketMultiBlockChangeSetting by setting("SPacketMultiBlockChange", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketMapsSetting by setting("SPacketMaps", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketKeepAliveSetting by setting("SPacketKeepAlive", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketJoinGameSetting by setting("SPacketJoinGame", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketHeldItemChangeSetting by setting("SPacketHeldItemChange", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketExplosionSetting by setting("SPacketExplosion", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketEntityVelocitySetting by setting("SPacketEntityVelocity", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityTeleportSetting by setting("SPacketEntityTeleport", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityStatusSetting by setting("SPacketEntityStatus", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityPropertiesSetting by setting("SPacketEntityProperties", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityMetadataSetting by setting("SPacketEntityMetadata", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityHeadLookSetting by setting("SPacketEntityHeadLook", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityEquipmentSetting by setting("SPacketEntityEquipment", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityEffectSetting by setting("SPacketEntityEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEntityAttachSetting by setting("SPacketEntityAttach", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketEffectSetting by setting("SPacketEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketDisplayObjectiveSetting by setting("SPacketDisplayObjective", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketDestroyEntitiesSetting by setting("SPacketDestroyEntities", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketCustomSoundSetting by setting("SPacketCustomSound", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketCustomPayloadSetting by setting("SPacketCustomPayload", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketCooldownSetting by setting("SPacketCooldown", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketConfirmTransactionSetting by setting("SPacketConfirmTransaction", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketCombatEventSetting by setting("SPacketCombatEvent", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketCollectItemSetting by setting("SPacketCollectItem", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private val SPacketCloseWindowSetting by setting("SPacketCloseWindow", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private val SPacketChunkDataSetting by setting("SPacketChunkData", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketChatSetting by setting("SPacketChat", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private val SPacketChangeGameStateSetting by setting("SPacketChangeGameState", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketCameraSetting by setting("SPacketCamera", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketBlockChangeSetting by setting("SPacketBlockChange", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketBlockBreakAnimSetting by setting("SPacketBlockBreakAnim", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketBlockActionSetting by setting("SPacketBlockAction", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private val SPacketAnimationSetting by setting("SPacketAnimation", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private val SPacketAdvancementInfoSetting by setting("SPacketAdvancementInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })

    private var numPackets = 0

    override fun getHudInfo(): String {
        return numPackets.toString()
    }

    init {
        onDisable {
            numPackets = 0
        }

        listener<PacketEvent.Send> {
            when (it.packet) {
                is CPacketAnimation -> if (CPacketAnimationSetting) it.cancel().also { numPackets++ }
                is CPacketUseEntity -> if (CPacketUseEntitySetting) it.cancel().also { numPackets++ }
                is CPacketChatMessage -> if (CPacketChatMessageSetting) it.cancel().also { numPackets++ }
                is CPacketClickWindow -> if (CPacketClickWindowSetting) it.cancel().also { numPackets++ }
                is CPacketClientSettings -> if (CPacketClientSettingsSetting) it.cancel().also { numPackets++ }
                is CPacketClientStatus -> if (CPacketClientStatusSetting) it.cancel().also { numPackets++ }
                is CPacketCloseWindow -> if (CPacketCloseWindowSetting) it.cancel().also { numPackets++ }
                is CPacketConfirmTeleport -> if (CPacketConfirmTeleportSetting) it.cancel().also { numPackets++ }
                is CPacketConfirmTransaction -> if (CPacketConfirmTransactionSetting) it.cancel().also { numPackets++ }
                is CPacketCreativeInventoryAction -> if (CPacketCreativeInventoryActionSetting) it.cancel().also { numPackets++ }
                is CPacketCustomPayload -> if (CPacketCustomPayloadSetting) it.cancel().also { numPackets++ }
                is CPacketEnchantItem -> if (CPacketEnchantItemSetting) it.cancel().also { numPackets++ }
                is CPacketEntityAction -> if (CPacketEntityActionSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.Position -> if (CPacketPlayerPositionSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.Rotation -> if (CPacketPlayerRotationSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.PositionRotation -> if (CPacketPlayerPositionRotationSetting) it.cancel().also { numPackets++ }
                is CPacketHeldItemChange -> if (CPacketHeldItemChangeSetting) it.cancel().also { numPackets++ }
                is CPacketInput -> if (CPacketInputSetting) it.cancel().also { numPackets++ }
                is CPacketPlaceRecipe -> if (CPacketPlaceRecipeSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerAbilities -> if (CPacketPlayerAbilitiesSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerTryUseItem -> if (CPacketPlayerTryUseItemSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerTryUseItemOnBlock -> if (CPacketPlayerTryUseItemOnBlockSetting) it.cancel().also { numPackets++ }
                is CPacketServerQuery -> if (CPacketServerQuerySetting) it.cancel().also { numPackets++ }
                is CPacketLoginStart -> if (CPacketLoginStartSetting) it.cancel().also { numPackets++ }
                is CPacketPing -> if (CPacketPingSetting) it.cancel().also { numPackets++ }
                is CPacketEncryptionResponse -> if (CPacketEncryptionResponseSetting) it.cancel().also { numPackets++ }
                is CPacketVehicleMove -> if (CPacketVehicleMoveSetting) it.cancel().also { numPackets++ }
                is CPacketUpdateSign -> if (CPacketUpdateSignSetting) it.cancel().also { numPackets++ }
                is CPacketTabComplete -> if (CPacketTabCompleteSetting) it.cancel().also { numPackets++ }
                is CPacketSteerBoat -> if (CPacketSteerBoatSetting) it.cancel().also { numPackets++ }
                is CPacketSpectate -> if (CPacketSpectateSetting) it.cancel().also { numPackets++ }
                is CPacketSeenAdvancements -> if (CPacketSeenAdvancementsSetting) it.cancel().also { numPackets++ }
                is CPacketResourcePackStatus -> if (CPacketResourcePackStatusSetting) it.cancel().also { numPackets++ }
                is CPacketRecipeInfo -> if (CPacketRecipeInfoSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerDigging -> if (CPacketPlayerDiggingSetting) it.cancel().also { numPackets++ }
                is CPacketKeepAlive -> if (CPacketKeepAliveSetting) it.cancel().also { numPackets++ }
            }
        }
        listener<PacketEvent.Receive> {
            when (it.packet) {
                is SPacketEntity.S17PacketEntityLookMove -> if (SPacketEntityS17PacketEntityLookMoveSetting) it.cancel().also { numPackets++ }
                is SPacketEntity.S16PacketEntityLook -> if (SPacketEntityS16PacketEntityLookSetting) it.cancel().also { numPackets++ }
                is SPacketEntity.S15PacketEntityRelMove -> if (SPacketEntityS15PacketEntityRelMoveSetting) it.cancel().also { numPackets++ }
                is SPacketServerInfo -> if (SPacketServerInfoSetting) it.cancel().also { numPackets++ }
                is SPacketLoginSuccess -> if (SPacketLoginSuccessSetting) it.cancel().also { numPackets++ }
                is SPacketWorldBorder -> if (SPacketWorldBorderSetting) it.cancel().also { numPackets++ }
                is SPacketWindowProperty -> if (SPacketWindowPropertySetting) it.cancel().also { numPackets++ }
                is SPacketWindowItems -> if (SPacketWindowItemsSetting) it.cancel().also { numPackets++ }
                is SPacketPong -> if (SPacketPongSetting) it.cancel().also { numPackets++ }
                is SPacketEncryptionRequest -> if (SPacketEncryptionRequestSetting) it.cancel().also { numPackets++ }
                is SPacketEnableCompression -> if (SPacketEnableCompressionSetting) it.cancel().also { numPackets++ }
                is SPacketDisconnect -> if (SPacketDisconnectSetting) it.cancel().also { numPackets++ }
                is SPacketUseBed -> if (SPacketUseBedSetting) it.cancel().also { numPackets++ }
                is SPacketUpdateTileEntity -> if (SPacketUpdateTileEntitySetting) it.cancel().also { numPackets++ }
                is SPacketUpdateScore -> if (SPacketUpdateScoreSetting) it.cancel().also { numPackets++ }
                is SPacketUpdateHealth -> if (SPacketUpdateHealthSetting) it.cancel().also { numPackets++ }
                is SPacketUpdateBossInfo -> if (SPacketUpdateBossInfoSetting) it.cancel().also { numPackets++ }
                is SPacketUnloadChunk -> if (SPacketUnloadChunkSetting) it.cancel().also { numPackets++ }
                is SPacketTitle -> if (SPacketTitleSetting) it.cancel().also { numPackets++ }
                is SPacketTimeUpdate -> if (SPacketTimeUpdateSetting) it.cancel().also { numPackets++ }
                is SPacketTeams -> if (SPacketTeamsSetting) it.cancel().also { numPackets++ }
                is SPacketTabComplete -> if (SPacketTabCompleteSetting) it.cancel().also { numPackets++ }
                is SPacketStatistics -> if (SPacketStatisticsSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnPosition -> if (SPacketSpawnPositionSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnPainting -> if (SPacketSpawnPaintingSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnObject -> if (SPacketSpawnObjectSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnPlayer -> if (SPacketSpawnPlayerSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnMob -> if (SPacketSpawnMobSetting) it.cancel().also { numPackets++ }
                is SPacketSpawnGlobalEntity -> if (SPacketSpawnGlobalEntitySetting) it.cancel().also { numPackets++ }
                is SPacketSpawnExperienceOrb -> if (SPacketSpawnExperienceOrbSetting) it.cancel().also { numPackets++ }
                is SPacketSoundEffect -> if (SPacketSoundEffectSetting) it.cancel().also { numPackets++ }
                is SPacketSignEditorOpen -> if (SPacketSignEditorOpenSetting) it.cancel().also { numPackets++ }
                is SPacketSetSlot -> if (SPacketSetSlotSetting) it.cancel().also { numPackets++ }
                is SPacketSetExperience -> if (SPacketSetExperienceSetting) it.cancel().also { numPackets++ }
                is SPacketServerDifficulty -> if (SPacketServerDifficultySetting) it.cancel().also { numPackets++ }
                is SPacketSelectAdvancementsTab -> if (SPacketSelectAdvancementsTabSetting) it.cancel().also { numPackets++ }
                is SPacketScoreboardObjective -> if (SPacketScoreboardObjectiveSetting) it.cancel().also { numPackets++ }
                is SPacketRespawn -> if (SPacketRespawnSetting) it.cancel().also { numPackets++ }
                is SPacketResourcePackSend -> if (SPacketResourcePackSendSetting) it.cancel().also { numPackets++ }
                is SPacketRemoveEntityEffect -> if (SPacketRemoveEntityEffectSetting) it.cancel().also { numPackets++ }
                is SPacketRecipeBook -> if (SPacketRecipeBookSetting) it.cancel().also { numPackets++ }
                is SPacketPlayerListItem -> if (SPacketPlayerListItemSetting) it.cancel().also { numPackets++ }
                is SPacketPlayerListHeaderFooter -> if (SPacketPlayerListHeaderFooterSetting) it.cancel().also { numPackets++ }
                is SPacketPlayerAbilities -> if (SPacketPlayerAbilitiesSetting) it.cancel().also { numPackets++ }
                is SPacketPlaceGhostRecipe -> if (SPacketPlaceGhostRecipeSetting) it.cancel().also { numPackets++ }
                is SPacketParticles -> if (SPacketParticlesSetting) it.cancel().also { numPackets++ }
                is SPacketOpenWindow -> if (SPacketOpenWindowSetting) it.cancel().also { numPackets++ }
                is SPacketMultiBlockChange -> if (SPacketMultiBlockChangeSetting) it.cancel().also { numPackets++ }
                is SPacketMaps -> if (SPacketMapsSetting) it.cancel().also { numPackets++ }
                is SPacketKeepAlive -> if (SPacketKeepAliveSetting) it.cancel().also { numPackets++ }
                is SPacketJoinGame -> if (SPacketJoinGameSetting) it.cancel().also { numPackets++ }
                is SPacketHeldItemChange -> if (SPacketHeldItemChangeSetting) it.cancel().also { numPackets++ }
                is SPacketExplosion -> if (SPacketExplosionSetting) it.cancel().also { numPackets++ }
                is SPacketEntityVelocity -> if (SPacketEntityVelocitySetting) it.cancel().also { numPackets++ }
                is SPacketEntityTeleport -> if (SPacketEntityTeleportSetting) it.cancel().also { numPackets++ }
                is SPacketEntityStatus -> if (SPacketEntityStatusSetting) it.cancel().also { numPackets++ }
                is SPacketEntityProperties -> if (SPacketEntityPropertiesSetting) it.cancel().also { numPackets++ }
                is SPacketEntityMetadata -> if (SPacketEntityMetadataSetting) it.cancel().also { numPackets++ }
                is SPacketEntityHeadLook -> if (SPacketEntityHeadLookSetting) it.cancel().also { numPackets++ }
                is SPacketEntityEquipment -> if (SPacketEntityEquipmentSetting) it.cancel().also { numPackets++ }
                is SPacketEntityEffect -> if (SPacketEntityEffectSetting) it.cancel().also { numPackets++ }
                is SPacketEntityAttach -> if (SPacketEntityAttachSetting) it.cancel().also { numPackets++ }.also { numPackets++ }
                is SPacketEffect -> if (SPacketEffectSetting) it.cancel().also { numPackets++ }
                is SPacketDisplayObjective -> if (SPacketDisplayObjectiveSetting) it.cancel().also { numPackets++ }
                is SPacketDestroyEntities -> if (SPacketDestroyEntitiesSetting) it.cancel().also { numPackets++ }
                is SPacketCustomSound -> if (SPacketCustomSoundSetting) it.cancel().also { numPackets++ }
                is SPacketCustomPayload -> if (SPacketCustomPayloadSetting) it.cancel().also { numPackets++ }
                is SPacketCooldown -> if (SPacketCooldownSetting) it.cancel().also { numPackets++ }
                is SPacketConfirmTransaction -> if (SPacketConfirmTransactionSetting) it.cancel().also { numPackets++ }
                is SPacketCombatEvent -> if (SPacketCombatEventSetting) it.cancel().also { numPackets++ }
                is SPacketCollectItem -> if (SPacketCollectItemSetting) it.cancel().also { numPackets++ }
                is SPacketCloseWindow -> if (SPacketCloseWindowSetting) it.cancel().also { numPackets++ }
                is SPacketChunkData -> if (SPacketChunkDataSetting) it.cancel().also { numPackets++ }
                is SPacketChat -> if (SPacketChatSetting) it.cancel().also { numPackets++ }
                is SPacketChangeGameState -> if (SPacketChangeGameStateSetting) it.cancel().also { numPackets++ }
                is SPacketCamera -> if (SPacketCameraSetting) it.cancel().also { numPackets++ }
                is SPacketBlockChange -> if (SPacketBlockChangeSetting) it.cancel().also { numPackets++ }
                is SPacketBlockBreakAnim -> if (SPacketBlockBreakAnimSetting) it.cancel().also { numPackets++ }
                is SPacketBlockAction -> if (SPacketBlockActionSetting) it.cancel().also { numPackets++ }
                is SPacketAnimation -> if (SPacketAnimationSetting) it.cancel().also { numPackets++ }
                is SPacketAdvancementInfo -> if (SPacketAdvancementInfoSetting) it.cancel().also { numPackets++ }
            }
        }
    }
}
