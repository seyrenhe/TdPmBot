package io.github.nekohasekai.pm.manage.menu

import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.*
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.BotIntegration
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.manage.BotHandler
import io.github.nekohasekai.pm.manage.MyBots
import td.TdApi

class IntegrationMenu : BotHandler() {

    companion object {

        const val payload = "set_integration"

        const val dataId = DATA_SET_START_INTEGRATION

    }

    override fun onLoad() {

        if (sudo is Launcher) {

            initData(dataId)

        }

        initStartPayload(payload)

    }

    fun integrationMenu(L: LocaleController, botUserId: Int, userBot: UserBot?, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        val integration = BotIntegration.Cache.fetch(botUserId).value

        val botUserName = botUserName(botUserId, userBot)

        var content = L.SET_INTEGRATION.input(
                botName(botUserId, userBot),
                botUserName(botUserId, userBot), when {
            integration == null -> L.INTEGRATION_UNDEF
            integration.paused -> L.INTEGRATION_PAUSED
            else -> L.INTEGRATION_OK
        })

        if (integration != null) {

            content += "\n\n" + L.INTEGRATION_STATUS.input(if (integration.adminOnly) L.ENABLED else L.DISABLED)

        }

        sudo make content withMarkup inlineButton {

            urlLine(L.INTEGRATION_SET, mkStartGroupPayloadUrl(botUserName, "set_integration"))

            val botId = botUserId.toByteArray()

            if (integration != null) {

                if (integration.adminOnly) {

                    dataLine(L.INTEGRATION_DISABLE_ADMIN_ONLY, dataId, botId, byteArrayOf(0))

                } else {

                    dataLine(L.INTEGRATION_ENABLE_ADMIN_ONLY, dataId, botId, byteArrayOf(1))

                }

                newLine {

                    if (!integration.paused) {

                        dataButton(L.INTEGRATION_PAUSE, dataId, botId, byteArrayOf(2))

                    } else {

                        dataButton(L.INTEGRATION_RESUME, dataId, botId, byteArrayOf(3))

                    }

                    dataButton(L.INTEGRATION_DEL, dataId, botId, byteArrayOf(4))

                }

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        val L = LocaleController.forChat(userId)

        if (data.isEmpty()) {

            integrationMenu(L, userBot?.botId ?: me.id, userBot, userId, chatId, messageId, true)

            return

        }

        val action = data[0][0].toInt()

        val integration = BotIntegration.Cache.fetch(botUserId).value

        if (integration == null) {

            // 过期的消息

            delete(chatId, messageId)

            findHandler<MyBots>().rootMenu(userId, chatId, 0L, false)

            return

        }

        when (action) {

            0 -> {

                // disable admin only

                database.write {

                    integration.adminOnly = false
                    integration.flush()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

            1 -> {

                database.write {

                    integration.adminOnly = true
                    integration.flush()

                }

                sudo makeAnswer L.ENABLED answerTo queryId

            }

            2 -> {

                database.write {

                    integration.paused = true
                    integration.flush()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

            3 -> {

                database.write {

                    integration.paused = false
                    integration.flush()

                }

                sudo makeAnswer L.ENABLED answerTo queryId

            }

            4 -> {

                database.write {

                    integration.delete()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

        }

        BotIntegration.Cache.fetch(botUserId).value = if (action < 4) integration else null

        integrationMenu(L, botUserId, userBot, userId, chatId, messageId, true)

    }

    override suspend fun onStartPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        val L = LocaleController.forChat(userId)

        if (userId.toLong() != Launcher.admin && database { UserBot.findById(me.id)?.owner != userId }) {

            // 权限检查

            warnUserCalled(userId, """
                Illegal access to set integration payload
                
                Chat: ${getChat(chatId).title}
                ChatId: $chatId
                User: ${getUser(userId).displayName}
                UserId: $userId
            """.trimIndent())

            sudo make L.NO_PERMISSION syncReplyTo message

            return

        }

        val integrationEntry = BotIntegration.Cache.fetch(me.id)

        val integration = integrationEntry.value

        if (integration == null) {

            BotIntegration.Cache.remove(me.id)

            database.write {

                BotIntegration.new(me.id) {

                    this.integration = chatId

                }

            }

        } else if (integration.integration != chatId) {

            database.write {

                integration.integration = chatId
                integration.flush()

            }

        } else if (integration.paused) {

            database.write {

                integration.paused = false
                integration.flush()

            }

        }

        sudo make L.INTEGRATION_HAS_SET syncReplyTo message

        Launcher.apply {

            val actionMessage = findHandler<MyBots>().actionMessages.fetch(userId)

            if (actionMessage.value != null) {

                findHandler<IntegrationMenu>().integrationMenu(L, me.id, null, userId, userId.toLong(), actionMessage.value!!, true)

            }

        }

    }

}