package com.lambda.client.module.modules.client

import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.items.shulkerList
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraftforge.fml.common.gameevent.TickEvent

object BuildTools : Module(
    name = "BuildTools",
    description = "Settings for internal build engine",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysEnabled = true
) {
    private val page by setting("Page", Page.BUILDING, description = "Switch between setting pages")

    /* behavior */
    val maxReach by setting("Max Reach", 4.9f, 1.0f..7.0f, 0.1f, { page == Page.BUILDING }, description = "Sets the range of the blueprint. Decrease when tasks fail!", unit = " blocks")
    val autoPathing by setting("Auto Pathing", true, { page == Page.BUILDING }, description = "Automatically pathfind to the next block")
    val pathingRecomputeTimeout by setting("Pathing Recompute Timeout", 200, 0..1000, 20, { page == Page.BUILDING }, description = "Timeout for recomputing the path", unit = " ms")
    val collectRange by setting("Collect Range", 8, 1..50, 1, { page == Page.BUILDING }, description = "Sets the range of the pickup", unit = " blocks")
    val minimumStackSize by setting("Minimum Stack Size To Collect", 32, 1..64, 1, { page == Page.BUILDING }, description = "Minimum amount of items to pick up", unit = " items")
//    val moveSpeed by setting("Packet Move Speed", 0.2, 0.0..1.0, 0.01, { page == Page.BEHAVIOR }, description = "Maximum player velocity per tick", unit = "m/t")
//    val taskTimeout by setting("Task Timeout", 8, 0..20, 1, { page == Page.BEHAVIOR }, description = "Timeout for waiting for the server to try again", unit = " ticks")
//    val maxRetries by setting("Max Task Retries", 3, 0..10, 1, { page == Page.BEHAVIOR }, description = "Maximum amount of timeouts for a task")
//    val rubberbandTimeout by setting("Rubberband Timeout", 50, 5..100, 5, { page == Page.BEHAVIOR }, description = "Timeout for pausing after a lag", unit = " ticks")
//    private val clearQueue by setting("Clear build queue", false, { page == Page.BEHAVIOR }, consumer = { _, it ->
//        if (it) BuildToolsManager.resetAll()
//        false
//    })
    val tickDelay by setting("Tick Delay", 0, 0..200, 1, { page == Page.BUILDING })

    /* mining */
    val breakDelay by setting("Break Delay", 1, 0..20, 1, { page == Page.BUILDING }, description = "Sets the delay ticks between break tasks", unit = " ticks")
//    val maxPending by setting("Max Pending", 1, 1..100, 1, { page == Page.BUILDING }, description = "Sets the maximum amount of pending break tasks")
    val miningSpeedFactor by setting("Mining Speed Factor", 1.0f, 0.0f..2.0f, 0.01f, { page == Page.BUILDING }, description = "Factor to manipulate calculated mining speed")
//    val interactionLimit by setting("Interaction Limit", 20, 1..100, 1, { page == Page.MINING }, description = "Set the interaction limit per second", unit = " interactions/s")
//    val multiBreak by setting("Multi Break", true, { page == Page.MINING }, description = "Breaks multiple instant breaking blocks intersecting with view vector on the same tick")
//    val packetFlood by setting("Packet Flood", false, { page == Page.MINING }, description = "Exploit for faster packet breaks. Sends START and STOP packet on same tick")

    /* placing */
    val placeDelay by setting("Place Delay", 1, 0..20, 1, { page == Page.BUILDING }, description = "Sets the delay ticks between placement tasks", unit = " ticks")
    val breakDownCycles by setting("Break Down", 64, 1..200, 1, { page == Page.BUILDING }, description = "", unit = " ender chests")
    val pickBlock by setting("Pick Block Creative", true, { page == Page.BUILDING }, description = "Use pick block to place blocks when in creative mode")
    val placeStrictness by setting("Placement Strictness", PlacementStrictness.DIRECTION, { page == Page.BUILDING }, description = "ANY: Allow all exposed surfaces. DIRECTION: Only allow surfaces in the direction of the player. VISIBLE: Only allow surfaces that are visible to the player.")
//    val illegalPlacements by setting("Illegal Placements", false, { page == Page.BUILDING }, description = "Do not use on 2b2t. Tries to interact with invisible surfaces")
//    val doPending by setting("Do Pending", true, { page == Page.BUILDING }, description = "Do not wait for server to confirm action")
    val executionCountPerTick by setting("Executions Per Tick", ActivityManager.MAX_DEPTH, 0..ActivityManager.MAX_DEPTH * 2, 1, { page == Page.BUILDING }, description = "How many tasks to execute per tick")
//    val scaffold by setting("Scaffold", true, { page == Page.PLACING }, description = "Tries to bridge / scaffold when stuck placing")
    val placementSearch by setting("Place Deep Search", 1, 1..4, 1, { page == Page.BUILDING }, description = "EXPERIMENTAL: Attempts to find a support block for placing against", unit = " blocks")
    val directionForce by setting("Block Direction Exploit", true, { page == Page.BUILDING }, description = "EXPLOIT: Forces the direction of the block to be placed")

    /* storage management */
    val storageManagement by setting("Manage Storage", true, { page == Page.STORAGE_MANAGEMENT }, description = "Choose to interact with container using only packets")
//    val searchEChest by setting("Search Ender Chest", false, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Allow access to your ender chest")
//    val leaveEmptyShulkers by setting("Leave Empty Shulkers", true, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Does not break empty shulkers")
//    val grindObsidian by setting("Grind Obsidian", true, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Destroy Ender Chests to obtain Obsidian")
//    val pickupRadius by setting("Pickup radius", 8, 1..50, 1, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Sets the radius for pickup", unit = " blocks")
//    val fastFill by setting("Fast Fill", true, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Moves as many item stacks to inventory as possible")
    val keepFreeSlots by setting("Free Slots", 1, 0..30, 1, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "How many inventory slots are untouched on refill", unit = " slots")
//    val lockSlotHotkey by setting("Lock Slot Hotkey", Bind(), { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Sets the hotkey for locking a slot")
//    val manageFood by setting("Manage Food", true, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Choose to manage food")
//    val manageTools by setting("Manage Tools", true, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Choose to manage food")
//    val leastTools by setting("Least Tools", 1, 0..36, 1, { page == Page.STORAGE_MANAGEMENT && manageTools && storageManagement }, description = "How many tools are saved")
//    val leastEnder by setting("Least Ender Chests", 1, 0..64, 1, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "How many ender chests are saved")
//    val leastFood by setting("Least Food", 1, 0..64, 1, { page == Page.STORAGE_MANAGEMENT && manageFood && storageManagement }, description = "How many food items are saved")
//    val preferEnderChests by setting("Prefer Ender Chests", false, { page == Page.STORAGE_MANAGEMENT && storageManagement }, description = "Prevent using raw material shulkers")

    /* tools */
    val usePickaxe by setting("Force Pickaxe", true, { page == Page.TOOLS }, description = "Use pickaxe to mine blocks")
    val useAxe by setting("Force Axe", true, { page == Page.TOOLS }, description = "Use axe to mine blocks")
    val useShovel by setting("Force Shovel", true, { page == Page.TOOLS }, description = "Use shovel to mine blocks")
    val useSword by setting("Force Sword", true, { page == Page.TOOLS }, description = "Use sword to mine blocks")
    val useShears by setting("Force Shears", true, { page == Page.TOOLS }, description = "Use shears to mine blocks")
    val minToolAmount by setting("Min Tool Amount", 1, 0..10, 1, { page == Page.TOOLS }, description = "How many tools are saved")

    /* render */
//    val info by setting("Show Info", true, { page == Page.RENDER }, description = "Prints session stats in chat")
//    val goalRender by setting("Baritone Goal", false, { page == Page.RENDER }, description = "Renders the baritone goal")
//    val showCurrentPos by setting("Current Pos", false, { page == Page.RENDER }, description = "Renders the current position")
    val filled by setting("Filled", true, { page == Page.RENDER }, description = "Renders colored task surfaces")
    val outline by setting("Outline", true, { page == Page.RENDER }, description = "Renders colored task outlines")
    val maxDistance by setting("Max Distance", 24, 0..256, 1, { page == Page.RENDER }, description = "Max distance to render tasks", unit = " blocks")
//    val popUp by setting("Pop up", true, { page == Page.RENDER }, description = "Funny render effect")
//    val popUpSpeed by setting("Pop up speed", 150, 0..500, 1, { page == Page.RENDER && popUp }, description = "Sets speed of the pop up effect", unit = "ms")
    val showDebugRender by setting("Debug Render", false, { page == Page.RENDER }, description = "Render debug info on tasks")
    val maxDebugRange by setting("Max Debug Range", 8, 0..256, 1, { page == Page.RENDER && showDebugRender }, description = "Max distance to render debug info", unit = " blocks")
    val maxDebugAmount by setting("Max Debug Amount", 20, 0..256, 1, { page == Page.RENDER && showDebugRender }, description = "Max amount of debug info to render")
    val textScale by setting("Text Scale", 1.0f, 0.0f..4.0f, 0.25f, { page == Page.RENDER && showDebugRender }, description = "Scale of debug text")
//    val distScaleFactor by setting("Distance Scale Factor", 0.05f, 0.0f..1.0f, 0.05f, { page == Page.RENDER && showDebugRender })
//    val minDistScale by setting("Min Distance Scale", 0.35f, 0.0f..1.0f, 0.05f, { page == Page.RENDER && showDebugRender })
    val aFilled by setting("Filled Alpha", 26, 0..255, 1, { filled && page == Page.RENDER }, description = "Sets the opacity")
    val aOutline by setting("Outline Alpha", 91, 0..255, 1, { outline && page == Page.RENDER }, description = "Sets the opacity")
    val thickness by setting("Thickness", 2.0f, 0.25f..4.0f, 0.25f, { outline && page == Page.RENDER }, description = "Sets thickness of outline")

    /* misc */
//    val disableWarnings by setting("Disable Warnings", false, { page == Page.MISC }, description = "DANGEROUS: Disable warnings on enable")
//    val debugLevel by setting("Debug Level", DebugLevel.IMPORTANT, { page == Page.MISC }, description = "Sets the debug log depth level")
//    val fakeSounds by setting("Fake Sounds", true, { page == Page.MISC }, description = "Adds artificial sounds to the actions")
//    val anonymizeLog by setting("Anonymize", false, { page == Page.MISC }, description = "Censors all coordinates in HUD and Chat")
//    val disableMode by setting("Disable Mode", DisableMode.NONE, { page == Page.MISC }, description = "Choose action when bot is out of materials or tools")
//    val usingProxy by setting("Proxy", false, { page == Page.MISC && disableMode == DisableMode.LOGOUT }, description = "Enable this if you are using a proxy to call the given command")
//    val proxyCommand by setting("Proxy Command", "/dc", { page == Page.MISC && disableMode == DisableMode.LOGOUT && usingProxy }, description = "Command to be sent to log out")

    private enum class Page {
        BUILDING, STORAGE_MANAGEMENT, TOOLS, RENDER
    }

    enum class DebugLevel {
        OFF, IMPORTANT, VERBOSE
    }

    enum class DisableMode {
        NONE, ANTI_AFK, LOGOUT
    }

    enum class PlacementStrictness {
        ANY, DIRECTION, VISIBLE
    }

    var defaultFillerMat: Block
        get() = Block.getBlockFromName(fillerMatSaved.value) ?: Blocks.NETHERRACK
        set(value) {
            fillerMatSaved.value = value.registryName.toString()
        }

    var defaultTool: Item
        get() = Item.getByNameOrId(tool.value) ?: Items.GOLDEN_APPLE
        set(value) {
            tool.value = value.registryName.toString()
        }

    var defaultFood: Item
        get() = Item.getByNameOrId(food.value) ?: Items.GOLDEN_APPLE
        set(value) {
            food.value = value.registryName.toString()
        }

    val defaultIgnoreBlocks = linkedSetOf(
        "minecraft:standing_sign",
        "minecraft:wall_sign",
        "minecraft:standing_banner",
        "minecraft:wall_banner"
    ).also { defaultIgnoreBlocks -> defaultIgnoreBlocks.addAll(shulkerList.map { it.registryName.toString() }) }

    val defaultEjectList = linkedSetOf(
        "minecraft:grass",
        "minecraft:dirt",
        "minecraft:netherrack",
        "minecraft:stone",
        "minecraft:cobblestone"
    )

    val ignoreBlocks = setting(CollectionSetting("IgnoreList", defaultIgnoreBlocks, { false }))
    var ignoredBlocks: List<Block> = ignoreBlocks.mapNotNull { Block.getBlockFromName(it) }
    init {
        ignoreBlocks.editListeners.add { ignoredBlocks = ignoreBlocks.mapNotNull { Block.getBlockFromName(it) } }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener
            if (ignoreBlocks.size != ignoredBlocks.size) {
                ignoreBlocks.filter { Block.getBlockFromName(it) == null }.forEach { ignoreBlocks.remove(it) }
                ignoredBlocks = ignoreBlocks.mapNotNull { Block.getBlockFromName(it) }
            }
        }
    }

    val ejectList = setting(CollectionSetting("Eject List", defaultEjectList, { false }))
    private val fillerMatSaved = setting("FillerMat", "minecraft:netherrack", { false })
    private val food = setting("FoodItem", "minecraft:golden_apple", { false })
    private val tool = setting("ToolItem", "minecraft:diamond_pickaxe", { false })
}