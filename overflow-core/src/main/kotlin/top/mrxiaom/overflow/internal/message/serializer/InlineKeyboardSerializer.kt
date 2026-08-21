package top.mrxiaom.overflow.internal.message.serializer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.mamoe.mirai.message.data.Message
import top.mrxiaom.overflow.internal.message.OnebotMessages.boolean
import top.mrxiaom.overflow.internal.message.OnebotMessages.int
import top.mrxiaom.overflow.internal.message.OnebotMessages.long
import top.mrxiaom.overflow.internal.message.OnebotMessages.string
import top.mrxiaom.overflow.message.data.InlineKeyboard
import top.mrxiaom.overflow.message.data.InlineKeyboardButton
import top.mrxiaom.overflow.message.data.InlineKeyboardRow
import top.mrxiaom.overflow.message.BuildMessageContext

class InlineKeyboardSerializer : AbstractMessageSerializer() {
    override fun isMatchJson(element: JsonObject): Boolean {
        return element["type"].string == "inline_keyboard"
    }

    override fun isMatchMirai(message: Message): Boolean {
        return message is InlineKeyboard
    }

    override suspend fun toMirai(
        context: BuildMessageContext.ToMirai,
        element: JsonObject
    ): Boolean {
        val data = element.data() ?: return false
        // OpenShamrock
        val botAppId = data["bot_appid"]?.long ?: 0L
        val rowsRaw = data["rows"]?.run {
            this as? JsonArray ?: runCatching { Json.parseToJsonElement(string).jsonArray }.getOrNull()
        } ?: return false
        val rows = rowsRaw.map { e1 ->
            val obj1 = e1.jsonObject
            InlineKeyboardRow(
                buttons = obj1["buttons"].asArrayOrEmpty().map { e2 ->
                    val button = e2.jsonObject
                    InlineKeyboardButton(
                        id = button["id"].string,
                        label = button["label"].string,
                        visitedLabel = button["visited_label"].string,
                        style = button["style"]?.int ?: 0,
                        type = button["type"]?.int ?: 0,
                        clickLimit = button["click_limit"]?.int ?: 0,
                        unsupportTips = button["unsupport_tips"].string,
                        data = button["data"].string,
                        atBotShowChannelList = button["at_bot_show_channel_list"]?.boolean == true,
                        permissionType = button["permission_type"]?.int ?: 0,
                        specifyRoleIds = button["specify_role_ids"].asArrayOrEmpty().map { it.string },
                        specifyTinyIds = (button["specify_tinyids"] ?: button["specify_user_ids"])
                            .asArrayOrEmpty().map { it.string }
                    )
                }
            )
        }
        context.add(InlineKeyboard(botAppId, rows))
        return true
    }

    override fun toJson(
        context: BuildMessageContext.ToJson,
        message: Message
    ): Boolean {
        val element = message as? InlineKeyboard ?: return false
        context.add("inline_keyboard") {
            put("bot_appid", element.botAppId)
            putJsonArray("rows") {
                element.rows.forEach { row ->
                    add(buildJsonObject row@{
                        putJsonArray("buttons") {
                            row.buttons.forEach { button ->
                                add(buildJsonObject {
                                    put("id", button.id)
                                    put("label", button.label)
                                    put("visited_label", button.visitedLabel)
                                    put("style", button.style)
                                    put("type", button.type)
                                    put("click_limit", button.clickLimit)
                                    put("unsupport_tips", button.unsupportTips)
                                    put("data", button.data)
                                    put("at_bot_show_channel_list", button.atBotShowChannelList)
                                    put("permission_type", button.permissionType)
                                    putJsonArray("specify_role_ids") {
                                        button.specifyRoleIds.forEach { add(it) }
                                    }
                                    putJsonArray("specify_tinyids") {
                                        button.specifyTinyIds.forEach { add(it) }
                                    }
                                    putJsonArray("specify_user_ids") {
                                        button.specifyTinyIds.forEach { add(it) }
                                    }
                                })
                            }
                        }
                    })
                }
            }
        }
        return true
    }

    private fun JsonElement?.asArrayOrEmpty(): JsonArray {
        return this as? JsonArray ?: JsonArray(emptyList())
    }
}
