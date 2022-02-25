package com.lambda.client.commons.utils

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object SystemUtils {

    fun setClipboard(text: String) {
        val selection = StringSelection(text)
        val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(selection, null)
    }

    fun getClipboard(): String? {
        val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return try {
            clipboard.getData(DataFlavor.stringFlavor)?.toString()
        } catch (e: Exception) {
            null
        }
    }

}
