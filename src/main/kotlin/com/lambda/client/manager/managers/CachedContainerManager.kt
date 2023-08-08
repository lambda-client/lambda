package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.player.PacketLogger
import com.lambda.client.module.modules.render.ContainerPreview.cacheEnderChests
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.ItemStackHelper
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.NonNullList
import java.io.File
import java.io.IOException
import java.nio.file.Paths

object CachedContainerManager : Manager {
    private val directory = Paths.get(FolderUtils.lambdaFolder, "cached-containers").toFile()
    private var echestFile: File? = null
    private var currentEnderChest: NonNullList<ItemStack>? = null

    init {
        listener<ConnectionEvent.Connect> {
            val serverDirectory = if (mc.integratedServer != null && mc.integratedServer?.isServerRunning == true) {
                mc.integratedServer?.folderName ?: run {
                    LambdaMod.LOG.info("Failed to get SP directory")
                    return@listener
                }
            } else {
                mc.currentServerData?.serverIP?.replace(":", "_")
                    ?: run {
                        LambdaMod.LOG.info("Failed to get server directory")
                        return@listener
                    }
            }

            val folder = File(directory, serverDirectory)
            echestFile = folder.toPath().resolve(mc.session.profile.id.toString()).resolve("echest.nbt").toFile()

            echestFile?.let { file ->
                try {
                    if (!file.exists()) {
                        if (!file.parentFile.exists()) file.parentFile.mkdirs()
                        file.createNewFile()
                    }
                } catch (e: IOException) {
                    LambdaMod.LOG.error("Failed to create ender chest file", e)
                }
            }
        }

        listener<ConnectionEvent.Disconnect> {
            echestFile = null
            currentEnderChest = null
        }
    }

    fun getEnderChestInventory(): NonNullList<ItemStack> {
        echestFile?.let { eFile ->
            currentEnderChest?.let { return it }
            if (!cacheEnderChests) return@let
            try {
                CompressedStreamTools.read(eFile)?.let { nbt ->
                    val inventory = NonNullList.withSize(27, ItemStack.EMPTY)
                    ItemStackHelper.loadAllItems(nbt, inventory)
                    currentEnderChest = inventory
                    return inventory
                }
            } catch (e: IOException) {
                currentEnderChest = NonNullList.withSize(27, ItemStack.EMPTY)
                LambdaMod.LOG.warn("${PacketLogger.chatName} Failed loading echest!", e)
            }

        }
        return NonNullList.withSize(27, ItemStack.EMPTY)
    }

    private fun saveEchest(inventory: NonNullList<ItemStack>) {
        if (!cacheEnderChests) return
        val currentEchestFile = echestFile ?: return
        val nonNullList = NonNullList.withSize(inventory.size, ItemStack.EMPTY)
        inventory.forEachIndexed { index, itemStack ->
            nonNullList[index] = itemStack
        }
        val nbt = NBTTagCompound()
        ItemStackHelper.saveAllItems(nbt, nonNullList)

        defaultScope.launch(Dispatchers.IO) {
            try {
                CompressedStreamTools.write(nbt, currentEchestFile)
            } catch (e: Throwable) {
                LambdaMod.LOG.warn("${PacketLogger.chatName} Failed saving echest!", e)
            }
        }
    }

    @JvmStatic
    fun setEnderChestInventory(inv: IInventory) {
        val inventory = NonNullList.withSize(inv.sizeInventory, ItemStack.EMPTY)
        for (i in 0 until inv.sizeInventory) {
            inventory[i] = inv.getStackInSlot(i)
        }
        currentEnderChest = inventory
        saveEchest(inventory)
    }

    @JvmStatic
    fun updateContainerInventory(windowId: Int) {
        val container = mc.player.openContainer
        if (container.windowId != windowId) return
        if (container !is ContainerChest) return
        val chest = container.lowerChestInventory
        if (chest !is InventoryBasic) return
        if (chest.name.contains("Ender Chest"))
            setEnderChestInventory(chest)
        // we can save other container inventories here but we also need the position
    }

    @JvmStatic
    fun onGuiChestClosed() {
        val container = mc.player.openContainer
        if (container !is ContainerChest) return
        val chest = container.lowerChestInventory
        if (chest !is InventoryBasic) return
        if (chest.name.contains("Ender Chest"))
            setEnderChestInventory(chest)
        // we can save other container inventories here but we also need the position
    }
}