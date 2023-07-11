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
    private val disableAllClient by setting("Disable All Client", false, { side == Side.CLIENT }, { _, _ ->
        CPacketAnimationSetting = false
        CPacketUseEntitySetting = false
        CPacketChatMessageSetting = false
        CPacketClickWindowSetting = false
        CPacketClientSettingsSetting = false
        CPacketClientStatusSetting = false
        CPacketCloseWindowSetting = false
        CPacketConfirmTeleportSetting = false
        CPacketConfirmTransactionSetting = false
        CPacketCreativeInventoryActionSetting = false
        CPacketCustomPayloadSetting = false
        CPacketEnchantItemSetting = false
        CPacketEntityActionSetting = false
        CPacketPlayerPositionSetting = false
        CPacketPlayerPositionRotationSetting = false
        CPacketHeldItemChangeSetting = false
        CPacketInputSetting = false
        CPacketPlaceRecipeSetting = false
        CPacketPlayerAbilitiesSetting = false
        CPacketPlayerTryUseItemSetting = false
        CPacketPlayerTryUseItemOnBlockSetting = false
        CPacketServerQuerySetting = false
        CPacketLoginStartSetting = false
        CPacketPingSetting = false
        CPacketEncryptionResponseSetting = false
        CPacketVehicleMoveSetting = false
        CPacketUpdateSignSetting = false
        CPacketTabCompleteSetting = false
        CPacketSteerBoatSetting = false
        CPacketSpectateSetting = false
        CPacketSeenAdvancementsSetting = false
        CPacketResourcePackStatusSetting = false
        CPacketRecipeInfoSetting = false
        CPacketPlayerDiggingSetting = false
        CPacketKeepAliveSetting = false
        false
    })

    private var CPacketAnimationSetting by setting("CPacketAnimation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketUseEntitySetting by setting("CPacketUseEntity", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var CPacketChatMessageSetting by setting("CPacketChatMessage", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketClickWindowSetting by setting("CPacketClickWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketClientSettingsSetting by setting("CPacketClient", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketClientStatusSetting by setting("CPacketClientStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketCloseWindowSetting by setting("CPacketCloseWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketConfirmTeleportSetting by setting("CPacketConfirmTeleport", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketConfirmTransactionSetting by setting("CPacketConfirmTransaction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketCreativeInventoryActionSetting by setting("CPacketCreativeInventoryAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketCustomPayloadSetting by setting("CPacketCustomPayload", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketEnchantItemSetting by setting("CPacketEnchantItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketEntityActionSetting by setting("CPacketEntityAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var CPacketPlayerPositionSetting by setting("CPacketPlayerPosition", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketPlayerRotationSetting by setting("CPacketPlayerRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketPlayerPositionRotationSetting by setting("CPacketPlayerPositionRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketHeldItemChangeSetting by setting("CPacketHeldItemChange", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketInputSetting by setting("CPacketInput", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketPlaceRecipeSetting by setting("CPacketPlaceRecipe", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketPlayerAbilitiesSetting by setting("CPacketPlayerAbilities", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketPlayerTryUseItemSetting by setting("CPacketPlayerTryUseItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketPlayerTryUseItemOnBlockSetting by setting("CPacketPlayerTryUseItemOnBlock", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketServerQuerySetting by setting("CPacketServerQuery", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketLoginStartSetting by setting("CPacketLoginStart", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketPingSetting by setting("CPacketPing", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketEncryptionResponseSetting by setting("CPacketEncryptionResponse", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketVehicleMoveSetting by setting("CPacketVehicleMove", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var CPacketUpdateSignSetting by setting("CPacketUpdateSign", false, { side == Side.CLIENT && categorySetting == CategorySlider.WORLD })
    private var CPacketTabCompleteSetting by setting("CPacketTabComplete", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketSteerBoatSetting by setting("CPacketSteerBoat", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var CPacketSpectateSetting by setting("CPacketSpectate", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketSeenAdvancementsSetting by setting("CPacketSeenAdvancements", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketResourcePackStatusSetting by setting("CPacketResourcePackStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var CPacketRecipeInfoSetting by setting("CPacketRecipeInfo", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var CPacketPlayerDiggingSetting by setting("CPacketPlayerDigging", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var CPacketKeepAliveSetting by setting("CPacketKeepAlive", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })

    private val disableAllServer by setting("Disable All Server", false, { side == Side.SERVER }, { _, _ ->
        SPacketEntityS17PacketEntityLookMoveSetting = false
        SPacketEntityS16PacketEntityLookSetting = false
        SPacketEntityS15PacketEntityRelMoveSetting = false
        SPacketServerInfoSetting = false
        SPacketLoginSuccessSetting = false
        SPacketWorldBorderSetting = false
        SPacketWindowPropertySetting = false
        SPacketWindowItemsSetting = false
        SPacketPongSetting = false
        SPacketEncryptionRequestSetting = false
        SPacketEnableCompressionSetting = false
        SPacketDisconnectSetting = false
        SPacketUseBedSetting = false
        SPacketUpdateTileEntitySetting = false
        SPacketUpdateScoreSetting = false
        SPacketUpdateHealthSetting = false
        SPacketUpdateBossInfoSetting = false
        SPacketUnloadChunkSetting = false
        SPacketTitleSetting = false
        SPacketTimeUpdateSetting = false
        SPacketTeamsSetting = false
        SPacketTabCompleteSetting = false
        SPacketStatisticsSetting = false
        SPacketSpawnPositionSetting = false
        SPacketSpawnPaintingSetting = false
        SPacketSpawnObjectSetting = false
        SPacketSpawnPlayerSetting = false
        SPacketSpawnMobSetting = false
        SPacketSpawnGlobalEntitySetting = false
        SPacketSpawnExperienceOrbSetting = false
        SPacketSoundEffectSetting = false
        SPacketSignEditorOpenSetting = false
        SPacketSetSlotSetting = false
        SPacketSetExperienceSetting = false
        SPacketServerDifficultySetting = false
        SPacketSelectAdvancementsTabSetting = false
        SPacketScoreboardObjectiveSetting = false
        SPacketRespawnSetting = false
        SPacketResourcePackSendSetting = false
        SPacketRemoveEntityEffectSetting = false
        SPacketRecipeBookSetting = false
        SPacketPlayerListItemSetting = false
        SPacketPlayerListHeaderFooterSetting = false
        SPacketPlayerAbilitiesSetting = false
        SPacketPlaceGhostRecipeSetting = false
        SPacketParticlesSetting = false
        SPacketOpenWindowSetting = false
        SPacketMultiBlockChangeSetting = false
        SPacketMapsSetting = false
        SPacketKeepAliveSetting = false
        SPacketJoinGameSetting = false
        SPacketHeldItemChangeSetting = false
        SPacketExplosionSetting = false
        SPacketEntityVelocitySetting = false
        SPacketEntityTeleportSetting = false
        SPacketEntityStatusSetting = false
        SPacketEntityPropertiesSetting = false
        SPacketEntityMetadataSetting = false
        SPacketEntityHeadLookSetting = false
        SPacketEntityEquipmentSetting = false
        SPacketEntityEffectSetting = false
        SPacketEntityAttachSetting = false
        SPacketEntityEffectSetting = false
        SPacketEntityAttachSetting = false
        SPacketEffectSetting = false
        SPacketDisplayObjectiveSetting = false
        SPacketDestroyEntitiesSetting = false
        SPacketCustomSoundSetting = false
        SPacketCustomPayloadSetting = false
        SPacketCooldownSetting = false
        SPacketConfirmTransactionSetting = false
        SPacketCombatEventSetting = false
        SPacketCollectItemSetting = false
        SPacketCloseWindowSetting = false
        SPacketChunkDataSetting = false
        SPacketChatSetting = false
        SPacketChangeGameStateSetting = false
        SPacketCameraSetting = false
        SPacketBlockChangeSetting = false
        SPacketBlockBreakAnimSetting = false
        SPacketBlockActionSetting = false
        SPacketAnimationSetting = false
        SPacketAdvancementInfoSetting = false
        false
    })

    private var SPacketEntityS17PacketEntityLookMoveSetting by setting("SPacketEntity.S17PacketEntityLookMove", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityS16PacketEntityLookSetting by setting("SPacketEntity.S16PacketEntityLook", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityS15PacketEntityRelMoveSetting by setting("SPacketEntity.S15PacketEntityRelMove", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketServerInfoSetting by setting("SPacketServerInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketLoginSuccessSetting by setting("SPacketLoginSuccess", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketWorldBorderSetting by setting("SPacketWorldBorder", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketWindowPropertySetting by setting("SPacketWindowProperty", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketWindowItemsSetting by setting("SPacketWindowItems", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketPongSetting by setting("SPacketPong", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketEncryptionRequestSetting by setting("SPacketEncryptionRequest", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketEnableCompressionSetting by setting("SPacketEnableCompression", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketDisconnectSetting by setting("SPacketDisconnect", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketUseBedSetting by setting("SPacketUseBed", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketUpdateTileEntitySetting by setting("SPacketUpdateTileEntity", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketUpdateScoreSetting by setting("SPacketUpdateScore", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketUpdateHealthSetting by setting("SPacketUpdateHealth", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketUpdateBossInfoSetting by setting("SPacketUpdateBossInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketUnloadChunkSetting by setting("SPacketUnloadChunk", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketTitleSetting by setting("SPacketTitle", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketTimeUpdateSetting by setting("SPacketTimeUpdate", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketTeamsSetting by setting("SPacketTeams", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketTabCompleteSetting by setting("SPacketTabComplete", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketStatisticsSetting by setting("SPacketStatistics", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketSpawnPositionSetting by setting("SPacketSpawnPosition", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketSpawnPaintingSetting by setting("SPacketSpawnPainting", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketSpawnObjectSetting by setting("SPacketSpawnObject", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketSpawnPlayerSetting by setting("SPacketSpawnPlayer", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketSpawnMobSetting by setting("SPacketSpawnMob", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketSpawnGlobalEntitySetting by setting("SPacketSpawnGlobalEntity", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketSpawnExperienceOrbSetting by setting("SPacketSpawnExperienceOrb", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketSoundEffectSetting by setting("SPacketSoundEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketSignEditorOpenSetting by setting("SPacketSignEditorOpen", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketSetSlotSetting by setting("SPacketSetSlot", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketSetExperienceSetting by setting("SPacketSetExperience", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketServerDifficultySetting by setting("SPacketServerDifficulty", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketSelectAdvancementsTabSetting by setting("SPacketSelectAdvancementsTab", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketScoreboardObjectiveSetting by setting("SPacketScoreboardObjective", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketRespawnSetting by setting("SPacketRespawn", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketResourcePackSendSetting by setting("SPacketResourcePackSend", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketRemoveEntityEffectSetting by setting("SPacketRemoveEntityEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketRecipeBookSetting by setting("SPacketRecipeBook", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketPlayerListItemSetting by setting("SPacketPlayerListItem", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketPlayerListHeaderFooterSetting by setting("SPacketPlayerListHeaderFooter", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketPlayerAbilitiesSetting by setting("SPacketPlayerAbilities", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketPlaceGhostRecipeSetting by setting("SPacketPlaceGhostRecipe", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketParticlesSetting by setting("SPacketParticles", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketOpenWindowSetting by setting("SPacketOpenWindow", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketMultiBlockChangeSetting by setting("SPacketMultiBlockChange", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketMapsSetting by setting("SPacketMaps", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketKeepAliveSetting by setting("SPacketKeepAlive", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketJoinGameSetting by setting("SPacketJoinGame", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketHeldItemChangeSetting by setting("SPacketHeldItemChange", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketExplosionSetting by setting("SPacketExplosion", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketEntityVelocitySetting by setting("SPacketEntityVelocity", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityTeleportSetting by setting("SPacketEntityTeleport", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityStatusSetting by setting("SPacketEntityStatus", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityPropertiesSetting by setting("SPacketEntityProperties", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityMetadataSetting by setting("SPacketEntityMetadata", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityHeadLookSetting by setting("SPacketEntityHeadLook", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityEquipmentSetting by setting("SPacketEntityEquipment", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityEffectSetting by setting("SPacketEntityEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEntityAttachSetting by setting("SPacketEntityAttach", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketEffectSetting by setting("SPacketEffect", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketDisplayObjectiveSetting by setting("SPacketDisplayObjective", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketDestroyEntitiesSetting by setting("SPacketDestroyEntities", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketCustomSoundSetting by setting("SPacketCustomSound", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketCustomPayloadSetting by setting("SPacketCustomPayload", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketCooldownSetting by setting("SPacketCooldown", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketConfirmTransactionSetting by setting("SPacketConfirmTransaction", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketCombatEventSetting by setting("SPacketCombatEvent", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketCollectItemSetting by setting("SPacketCollectItem", false, { side == Side.SERVER && categorySetting == CategorySlider.ENTITY })
    private var SPacketCloseWindowSetting by setting("SPacketCloseWindow", false, { side == Side.SERVER && categorySetting == CategorySlider.INVENTORY })
    private var SPacketChunkDataSetting by setting("SPacketChunkData", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketChatSetting by setting("SPacketChat", false, { side == Side.SERVER && categorySetting == CategorySlider.SYSTEM })
    private var SPacketChangeGameStateSetting by setting("SPacketChangeGameState", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketCameraSetting by setting("SPacketCamera", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketBlockChangeSetting by setting("SPacketBlockChange", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketBlockBreakAnimSetting by setting("SPacketBlockBreakAnim", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketBlockActionSetting by setting("SPacketBlockAction", false, { side == Side.SERVER && categorySetting == CategorySlider.WORLD })
    private var SPacketAnimationSetting by setting("SPacketAnimation", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })
    private var SPacketAdvancementInfoSetting by setting("SPacketAdvancementInfo", false, { side == Side.SERVER && categorySetting == CategorySlider.PLAYER })

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
