package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper

object LicenseCommand : ClientCommand(
    name = "license",
    description = "Information about Lambda's license"
) {
    init {
        execute {
            MessageSendHelper.sendChatMessage("You can view Lambda's &7client&f License (LGPLv3) at &9https://github.com/lambda-client/lambda/blob/master/LICENSE.md")
        }
    }
}