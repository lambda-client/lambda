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
        cPacketAnimationSetting = false
        cPacketUseEntitySetting = false
        cPacketChatMessageSetting = false
        cPacketClickWindowSetting = false
        cPacketClientSettingsSetting = false
        cPacketClientStatusSetting = false
        cPacketCloseWindowSetting = false
        cPacketConfirmTeleportSetting = false
        cPacketConfirmTransactionSetting = false
        cPacketCreativeInventoryActionSetting = false
        cPacketCustomPayloadSetting = false
        cPacketEnchantItemSetting = false
        cPacketEntityActionSetting = false
        cPacketPlayerPositionSetting = false
        cPacketPlayerPositionRotationSetting = false
        cPacketHeldItemChangeSetting = false
        cPacketInputSetting = false
        cPacketPlaceRecipeSetting = false
        cPacketPlayerAbilitiesSetting = false
        cPacketPlayerTryUseItemSetting = false
        cPacketPlayerTryUseItemOnBlockSetting = false
        cPacketServerQuerySetting = false
        cPacketLoginStartSetting = false
        cPacketPingSetting = false
        cPacketEncryptionResponseSetting = false
        cPacketVehicleMoveSetting = false
        cPacketUpdateSignSetting = false
        cPacketTabCompleteSetting = false
        cPacketSteerBoatSetting = false
        cPacketSpectateSetting = false
        cPacketSeenAdvancementsSetting = false
        cPacketResourcePackStatusSetting = false
        cPacketRecipeInfoSetting = false
        cPacketPlayerDiggingSetting = false
        cPacketKeepAliveSetting = false
        false
    })

    private var cPacketAnimationSetting by setting("CPacketAnimation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketUseEntitySetting by setting("CPacketUseEntity", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketChatMessageSetting by setting("CPacketChatMessage", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketClickWindowSetting by setting("CPacketClickWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketClientSettingsSetting by setting("CPacketClient", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketClientStatusSetting by setting("CPacketClientStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketCloseWindowSetting by setting("CPacketCloseWindow", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketConfirmTeleportSetting by setting("CPacketConfirmTeleport", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketConfirmTransactionSetting by setting("CPacketConfirmTransaction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketCreativeInventoryActionSetting by setting("CPacketCreativeInventoryAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketCustomPayloadSetting by setting("CPacketCustomPayload", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketEnchantItemSetting by setting("CPacketEnchantItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketEntityActionSetting by setting("CPacketEntityAction", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketPlayerPositionSetting by setting("CPacketPlayerPosition", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerRotationSetting by setting("CPacketPlayerRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerPositionRotationSetting by setting("CPacketPlayerPositionRotation", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketHeldItemChangeSetting by setting("CPacketHeldItemChange", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketInputSetting by setting("CPacketInput", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlaceRecipeSetting by setting("CPacketPlaceRecipe", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketPlayerAbilitiesSetting by setting("CPacketPlayerAbilities", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerTryUseItemSetting by setting("CPacketPlayerTryUseItem", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketPlayerTryUseItemOnBlockSetting by setting("CPacketPlayerTryUseItemOnBlock", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketServerQuerySetting by setting("CPacketServerQuery", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketLoginStartSetting by setting("CPacketLoginStart", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketPingSetting by setting("CPacketPing", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketEncryptionResponseSetting by setting("CPacketEncryptionResponse", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketVehicleMoveSetting by setting("CPacketVehicleMove", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketUpdateSignSetting by setting("CPacketUpdateSign", false, { side == Side.CLIENT && categorySetting == CategorySlider.WORLD })
    private var cPacketTabCompleteSetting by setting("CPacketTabComplete", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketSteerBoatSetting by setting("CPacketSteerBoat", false, { side == Side.CLIENT && categorySetting == CategorySlider.ENTITY })
    private var cPacketSpectateSetting by setting("CPacketSpectate", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketSeenAdvancementsSetting by setting("CPacketSeenAdvancements", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketResourcePackStatusSetting by setting("CPacketResourcePackStatus", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })
    private var cPacketRecipeInfoSetting by setting("CPacketRecipeInfo", false, { side == Side.CLIENT && categorySetting == CategorySlider.INVENTORY })
    private var cPacketPlayerDiggingSetting by setting("CPacketPlayerDigging", false, { side == Side.CLIENT && categorySetting == CategorySlider.PLAYER })
    private var cPacketKeepAliveSetting by setting("CPacketKeepAlive", false, { side == Side.CLIENT && categorySetting == CategorySlider.SYSTEM })

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
                is CPacketAnimation -> if (cPacketAnimationSetting) it.cancel().also { numPackets++ }
                is CPacketUseEntity -> if (cPacketUseEntitySetting) it.cancel().also { numPackets++ }
                is CPacketChatMessage -> if (cPacketChatMessageSetting) it.cancel().also { numPackets++ }
                is CPacketClickWindow -> if (cPacketClickWindowSetting) it.cancel().also { numPackets++ }
                is CPacketClientSettings -> if (cPacketClientSettingsSetting) it.cancel().also { numPackets++ }
                is CPacketClientStatus -> if (cPacketClientStatusSetting) it.cancel().also { numPackets++ }
                is CPacketCloseWindow -> if (cPacketCloseWindowSetting) it.cancel().also { numPackets++ }
                is CPacketConfirmTeleport -> if (cPacketConfirmTeleportSetting) it.cancel().also { numPackets++ }
                is CPacketConfirmTransaction -> if (cPacketConfirmTransactionSetting) it.cancel().also { numPackets++ }
                is CPacketCreativeInventoryAction -> if (cPacketCreativeInventoryActionSetting) it.cancel().also { numPackets++ }
                is CPacketCustomPayload -> if (cPacketCustomPayloadSetting) it.cancel().also { numPackets++ }
                is CPacketEnchantItem -> if (cPacketEnchantItemSetting) it.cancel().also { numPackets++ }
                is CPacketEntityAction -> if (cPacketEntityActionSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.Position -> if (cPacketPlayerPositionSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.Rotation -> if (cPacketPlayerRotationSetting) it.cancel().also { numPackets++ }
                is CPacketPlayer.PositionRotation -> if (cPacketPlayerPositionRotationSetting) it.cancel().also { numPackets++ }
                is CPacketHeldItemChange -> if (cPacketHeldItemChangeSetting) it.cancel().also { numPackets++ }
                is CPacketInput -> if (cPacketInputSetting) it.cancel().also { numPackets++ }
                is CPacketPlaceRecipe -> if (cPacketPlaceRecipeSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerAbilities -> if (cPacketPlayerAbilitiesSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerTryUseItem -> if (cPacketPlayerTryUseItemSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerTryUseItemOnBlock -> if (cPacketPlayerTryUseItemOnBlockSetting) it.cancel().also { numPackets++ }
                is CPacketServerQuery -> if (cPacketServerQuerySetting) it.cancel().also { numPackets++ }
                is CPacketLoginStart -> if (cPacketLoginStartSetting) it.cancel().also { numPackets++ }
                is CPacketPing -> if (cPacketPingSetting) it.cancel().also { numPackets++ }
                is CPacketEncryptionResponse -> if (cPacketEncryptionResponseSetting) it.cancel().also { numPackets++ }
                is CPacketVehicleMove -> if (cPacketVehicleMoveSetting) it.cancel().also { numPackets++ }
                is CPacketUpdateSign -> if (cPacketUpdateSignSetting) it.cancel().also { numPackets++ }
                is CPacketTabComplete -> if (cPacketTabCompleteSetting) it.cancel().also { numPackets++ }
                is CPacketSteerBoat -> if (cPacketSteerBoatSetting) it.cancel().also { numPackets++ }
                is CPacketSpectate -> if (cPacketSpectateSetting) it.cancel().also { numPackets++ }
                is CPacketSeenAdvancements -> if (cPacketSeenAdvancementsSetting) it.cancel().also { numPackets++ }
                is CPacketResourcePackStatus -> if (cPacketResourcePackStatusSetting) it.cancel().also { numPackets++ }
                is CPacketRecipeInfo -> if (cPacketRecipeInfoSetting) it.cancel().also { numPackets++ }
                is CPacketPlayerDigging -> if (cPacketPlayerDiggingSetting) it.cancel().also { numPackets++ }
                is CPacketKeepAlive -> if (cPacketKeepAliveSetting) it.cancel().also { numPackets++ }
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
