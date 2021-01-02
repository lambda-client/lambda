package me.zeroeightsix.kami.module.modules.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.command.CommandManager
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.manager.managers.MessageManager
import me.zeroeightsix.kami.manager.managers.MessageManager.newMessageModifier
import me.zeroeightsix.kami.mixin.extension.packetMessage
import me.zeroeightsix.kami.mixin.extension.textComponent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.text.MessageDetection
import me.zeroeightsix.kami.util.text.MessageSendHelper
import me.zeroeightsix.kami.util.text.formatValue
import me.zeroeightsix.kami.util.threads.defaultScope
import net.minecraft.network.play.server.SPacketChat
import net.minecraft.util.text.TextFormatting
import org.kamiblue.commons.extension.random
import org.kamiblue.commons.extension.remove
import org.kamiblue.commons.utils.SystemUtils
import org.kamiblue.event.listener.listener
import java.util.*

// TODO: for GUI branch: Update instructions to not use commands
// The old gui doesn't support string settings
// TODO: Add proper RSA encryption
@Module.Info(
    name = "EncryptChat",
    description = "Encrypts and decrypts chat messages",
    category = Module.Category.CHAT
)
object EncryptChat : Module() {
    private val commands = register(Settings.b("Commands", false))
    private val self = register(Settings.b("DecryptOwn", true))
    private val key = register(Settings.stringBuilder("key").withValue("defaultKey"))
    val delimiter = register(Settings.s("Delimiter", "%"))

    private const val personFrowning = "\uD83D\uDE4D"
    private val chars = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        '-', '_', '/', ';', '=', '?', '+', '\u00B5', '\u00A3', '*', '^', '\u00F9', '$', '!', '{', '}', '\'', '"', '|', '&', ' '
    )

    private val modifier = newMessageModifier(
        filter = {
            (commands.value || MessageDetection.Command.ANY_EXCEPT_DELIMITER detectNot it.packet.message)
        },
        modifier = {
            it.crypt() ?: it.packet.message
        }
    )

    override fun onEnable() {
        keyValue
        modifier.enable()
    }

    override fun onDisable() {
        modifier.disable()
    }

    private val keyValue: String
        get() {
            var keyTmp = key.value

            if (key.value == "defaultKey") {
                defaultScope.launch(Dispatchers.IO) {
                    keyTmp = chars.remove('&').remove(' ').random(32)
                    SystemUtils.copyToClipboard(keyTmp)

                    MessageSendHelper.sendChatMessage("$chatName Your encryption key was set to '&7$keyTmp&f', and copied to your clipboard.\n" +
                        "You may change it with " +
                        formatValue("${CommandManager.prefix}set $name key <newKey>")
                    )

                    key.value = keyTmp
                }
            }

            return keyTmp
        }

    init {
        listener<PacketEvent.Receive> {
            if (it.packet !is SPacketChat) return@listener
            var message = it.packet.textComponent.unformattedText

            if (!self.value && MessageDetection.Message.SELF detect message) return@listener

            val username = MessageDetection.Message.ANY.playerName(message) ?: "Unknown User"

            if (!message.contains(personFrowning)) return@listener

            message = MessageDetection.Message.ANY.removed(message) ?: return@listener
            val sub = message.split(personFrowning)

            val builder = StringBuilder()

            for (i in sub.indices) {
                if (i % 2 == 0) {
                    builder.append(sub[i])
                } else {
                    builder.append(decode(sub[i]))
                }
            }

            val final = builder.toString()
            if (final == message) return@listener // prevent further possible issues
            MessageSendHelper.sendRawChatMessage("<$username> ${TextFormatting.BOLD}DECRYPTED${TextFormatting.RESET}: $final")
        }
    }

    private var previousMessage = ""

    private fun MessageManager.QueuedMessage.crypt(): String? {
        var msg = packet.packetMessage
        if (msg == previousMessage) { // fix existing issue - for some reason this is run twice sometimes (single player only)
            previousMessage = ""
            return null
        }

        val sub = msg.split(delimiter.value)

        val builder = StringBuilder()

        for (i in sub.indices) {
            if (i % 2 == 0) {
                builder.append(sub[i])
            } else {
                builder.append(personFrowning)
                builder.append(encode(sub[i]))
                builder.append(personFrowning)
            }
        }

        val built = builder.toString()
        if (built.isBlank()) return null

        msg = built

        if (msg.length > 256) { // this shouldn't happen unless your message was already around 254 chars?
            MessageSendHelper.sendChatMessage("Encrypted message length was too long, couldn't send!")
            return null
        }

        previousMessage = msg
        return msg
    }

    private fun encode(s: String): String {
        val builder = StringBuilder()

        val key = keyValue
        builder.append(encodeVignere(key, s))

        return builder.toString()
    }

    private fun decode(s: String): String {
        val builder = StringBuilder()

        val key = keyValue
        builder.append(decodeVignere(key, s))

        return builder.toString()
    }

    private val cypher: Array<Array<Char>> =
        Array(chars.size) { i ->
            Array(chars.size) {
                chars[getSensibleIndex(it + i)]
            }
        }

    private fun getSensibleIndex(origin: Int): Int {
        return if (origin >= chars.size) {
            origin - chars.size
        } else {
            origin
        }
    }

    private fun encodeVignere(key: String, text: String): String {
        var endText = ""
        var keyPos = 0

        for (char in text.toCharArray()) {

            val index = chars.indexOf(char)

            if (index == -1) {
                continue
            }

            endText += cypher[chars.indexOf(key[keyPos])][index]
            keyPos++

            if (keyPos >= key.length) {
                keyPos -= key.length
            }
        }

        return endText
    }

    private fun decodeVignere(key: String, text: String): String {
        var endText = ""
        var keyPos = 0

        for (char in text.toCharArray()) {
            endText += chars[cypher[chars.indexOf(key[keyPos])].indexOf(char)]
            keyPos++

            if (keyPos >= key.length) {
                keyPos -= key.length
            }
        }

        return endText
    }
}