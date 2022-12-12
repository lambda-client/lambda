package com.lambda.client.module.modules.chat

import com.lambda.client.LambdaMod
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageDetection
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.SpamFilters
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.event.ClientChatReceivedEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object AntiSpam : Module(
    name = "AntiSpam",
    description = "Removes spam and advertising from the chat",
    category = Category.CHAT,
    showOnArray = false
) {
    private val mode by setting("Mode", Mode.REPLACE)
    private val replaceMode by setting("Replace Mode", ReplaceMode.ASTERISKS, { mode == Mode.REPLACE })
    private val page by setting("Page", Page.TYPE)

    /* Page One */
    private val discordLinks = setting("Discord", true, { page == Page.TYPE })
    private val slurs = setting("Slurs", true, { page == Page.TYPE })
    private val swears = setting("Swears", false, { page == Page.TYPE })
    private val automated = setting("Automated", true, { page == Page.TYPE })
    private val ips = setting("Server Ips", true, { page == Page.TYPE })
    private val specialCharEnding = setting("Special Ending", true, { page == Page.TYPE })
    private val specialCharBegin = setting("Special Begin", true, { page == Page.TYPE })
    private val greenText = setting("Green Text", false, { page == Page.TYPE })
    private val fancyChat by setting("Fancy Chat", false, { page == Page.TYPE })
    private val lagMessage by setting("Lag Message", true, { page == Page.TYPE })
    private val thresholdLagMessage by setting("Threshold Lag", 256, 256..1024, 1, { page == Page.TYPE && lagMessage }) // Is 1024 the max?

    /* Page Two */
    private val aggressiveFiltering by setting("Aggressive Filtering", true, { page == Page.SETTINGS })
    private val duplicates by setting("Duplicates", true, { page == Page.SETTINGS })
    private val duplicatesTimeout by setting("Duplicates Timeout", 30, 1..600, 5, { duplicates && page == Page.SETTINGS }, unit = "s")
    private val filterOwn by setting("Filter Own", false, { page == Page.SETTINGS })
    private val filterDMs by setting("Filter DMs", false, { page == Page.SETTINGS })
    private val filterServer by setting("Filter Server", false, { page == Page.SETTINGS })
    private val showBlocked by setting("Show Blocked", ShowBlocked.LOG_FILE, { page == Page.SETTINGS })

    private enum class Mode {
        REPLACE, HIDE
    }

    @Suppress("unused")
    private enum class ReplaceMode(val redaction: String) {
        REDACTED("[redacted]"), ASTERISKS("****")
    }

    private enum class Page {
        TYPE, SETTINGS
    }

    @Suppress("unused")
    private enum class ShowBlocked {
        NONE, LOG_FILE, CHAT, BOTH
    }

    private val messageHistory = ConcurrentHashMap<String, Long>()
    private val settingMap = hashMapOf(
        greenText to SpamFilters.greenText,
        specialCharBegin to SpamFilters.specialBeginning,
        specialCharEnding to SpamFilters.specialEnding,
        automated to SpamFilters.ownsMeAndAll,
        automated to SpamFilters.thanksTo,
        discordLinks to SpamFilters.discordInvite,
        ips to SpamFilters.ipAddress,
        automated to SpamFilters.announcer,
        automated to SpamFilters.spammer,
        automated to SpamFilters.insulter,
        automated to SpamFilters.greeter,
        slurs to SpamFilters.slurs,
        swears to SpamFilters.swears
    )

    init {
        onDisable {
            messageHistory.clear()
        }

        safeListener<ClientChatReceivedEvent> { event ->
            if (lagMessage && isLagMessage(event.message.unformattedText)) {
                event.isCanceled = true
            }

            messageHistory.values.removeIf { System.currentTimeMillis() - it > 600000 }

            if (duplicates && checkDupes(event.message.unformattedText)) {
                event.isCanceled = true
            }

            val pattern = isSpam(event.message.unformattedText)

            pattern?.let {
                when (mode) {
                    Mode.HIDE -> {
                        event.isCanceled = true
                    }
                    Mode.REPLACE -> {
                        event.message = TextComponentString(sanitize(event.message.formattedText, pattern, replaceMode.redaction))
                    }
                }
            }

            if (fancyChat) {
                val message = sanitizeFancyChat(event.message.unformattedText)
                if (message.trim { it <= ' ' }.isEmpty()) { // this should be removed if we are going for an intelligent de-fancy
                    event.message = TextComponentString(getUsername(event.message.unformattedText) + " [Fancychat]")
                }
            }
        }
    }

    private fun sanitize(toClean: String, matcher: String, replacement: String): String {
        return if (!aggressiveFiltering) {
            toClean.replace("\\b$matcher|$matcher\\b".toRegex(), replacement) // only check for start or end of a word
        } else { // We might encounter the scunthorpe problem, so aggressive mode is off by default.
            toClean.replace(matcher.toRegex(), replacement)
        }
    }

    private fun isSpam(message: String): String? {
        return if (!filterOwn && isOwn(message)
            || !filterDMs && MessageDetection.Direct.ANY detect message
            || !filterServer && MessageDetection.Server.ANY detect message) {
            null
        } else {
            detectSpam(removeUsername(message))
        }
    }

    private fun isLagMessage(message: String): Boolean {
        return if (!filterOwn && isOwn(message)
            || !filterDMs && MessageDetection.Direct.ANY detect message
            || !filterServer && MessageDetection.Server.ANY detect message) {
            false
        } else {
            message.getBytes() > thresholdLagMessage
        }
    }

    private fun detectSpam(message: String): String? {
        for ((key, value) in settingMap) {
            findPatterns(value, message)?.let {
                if (key.value) {
                    sendResult(key.name, message)
                    return it
                }
            }
        }
        return null
    }

    private fun removeUsername(username: String): String {
        return username.replace("<[^>]*> ".toRegex(), "")
    }

    private fun getUsername(rawMessage: String): String? {
        val matcher = Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE).matcher(rawMessage)
        return if (matcher.find()) {
            matcher.group()
        } else {
            rawMessage.substring(0, rawMessage.indexOf(">")) // a bit hacky
        }
    }

    private fun checkDupes(message: String): Boolean {
        var isDuplicate = false

        if (messageHistory.containsKey(message) &&
            (System.currentTimeMillis() - messageHistory[message]!!) / 1000 < duplicatesTimeout) isDuplicate = true
        messageHistory[message] = System.currentTimeMillis()

        if (isDuplicate) {
            sendResult("Duplicate", message)
        }
        return isDuplicate
    }

    private fun String.getBytes(): Int {
        return this.toByteArray().size
    }

    private fun isOwn(message: String): Boolean {
        /* mc.player is null when the module is being registered, so this matcher isn't added alongside the other FilterPatterns */
        val ownFilter = "^<" + mc.player.name + "> "
        return Pattern.compile(ownFilter, Pattern.CASE_INSENSITIVE).matcher(message).find()
    }

    private fun findPatterns(patterns: Array<String>, string: String): String? {
        var cString = string
        cString = cString.replace("<[^>]*> ".toRegex(), "") // remove username first
        for (pattern in patterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(cString).find()) {
                return pattern
            }
        }
        return null
    }

    private fun sanitizeFancyChat(toClean: String): String {
        // this has the potential to be intelligent and convert to ascii instead of just delete
        return toClean.replace("[^\\u0000-\\u007F]".toRegex(), "")
    }

    private fun sendResult(name: String, message: String) {
        if (showBlocked == ShowBlocked.CHAT || showBlocked == ShowBlocked.BOTH) MessageSendHelper.sendChatMessage("$chatName $name: $message")
        if (showBlocked == ShowBlocked.LOG_FILE || showBlocked == ShowBlocked.BOTH) LambdaMod.LOG.info("$chatName $name: $message")
    }
}
