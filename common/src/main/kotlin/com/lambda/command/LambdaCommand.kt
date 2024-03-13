package com.lambda.command

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.util.Nameable
import com.lambda.util.text.*
import net.minecraft.text.Text
import java.util.*

abstract class LambdaCommand : Nameable {
    fun SafeContext.sendSuccess(text: Text) {
        player.sendMessage(buildText {
            literal(" ")
            styled(Color.GREEN) {
                literal(Lambda.SYMBOL)
            }
            styled(Color.GREY) {
                literal(" ${name.capitalize()} Command ")
            }
            text(text)
        })
    }
}