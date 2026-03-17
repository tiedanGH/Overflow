package top.mrxiaom.overflow.internal

import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.MiraiLogger
import org.slf4j.Logger
import top.mrxiaom.overflow.BotBuilder
import top.mrxiaom.overflow.internal.utils.SLF4JAdapterLogger

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class Config(
    @SerialName("no_log___DO_NOT_REPORT_IF_YOU_SWITCH_THIS_ON")
    var noLogDoNotReportIfYouSwitchThisOn: Boolean = false,
    @SerialName("connections")
    var connectionsRaw: List<JsonElement> = listOf(
        buildJsonObject {
            put("enable", false)
            put("type", "websocket")
            put("host", "ws://127.0.0.1:3001")
            put("token", "")
        },
        buildJsonObject {
            put("enable", false)
            put("type", "websocket-reverse")
            put("port", 3002)
            put("token", "")
        },
    ),
    @SerialName("no_platform")
    var noPlatform: Boolean = false,
    @SerialName("use_cq_code")
    var useCQCode: Boolean = false,
    @SerialName("retry_times")
    var retryTimes: Int = 5,
    @SerialName("retry_wait_mills")
    var retryWaitMills: Long = 5_000L,
    @SerialName("retry_rest_mills")
    var retryRestMills: Long = 60_000L,
    @SerialName("heartbeat_check_seconds")
    var heartbeatCheckSeconds: Int = 60,
    @SerialName("use_group_upload_event_for_file_message")
    var useGroupUploadEventForFileMessage: Boolean = false,
    @SerialName("resource_cache")
    var resourceCache: CacheConfig = CacheConfig(),
    @SerialName("drop_events_before_connected")
    var dropEventsBeforeConnected: Boolean = true,
) {
    internal val connections: List<IConnection> by lazy {
        connectionsRaw.map { element ->
            val type = element.jsonObject["type"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("配置项缺少 type 字段")

            return@map when (type) {
                "websocket" -> JSON.decodeFromJsonElement(ConnWebSocket.serializer(), element).invoke(this)
                "websocket-reverse" -> JSON.decodeFromJsonElement(ConnWebSocketReverse.serializer(), element).invoke(this)
                else -> throw IllegalArgumentException("未知的连接类型 $type")
            }
        }
    }
}
@Serializable
data class CacheConfig (
    @SerialName("enabled")
    var enabled: Boolean = false,
    @SerialName("keep_duration_hours")
    var keepDurationHours: Long = 168L,
)

internal interface IConnection {
    val enabled: Boolean
    suspend fun connect(
        printInfo: Boolean = false,
        overrideLogger: Logger? = null,
        job: Job? = null,
    ): Bot?
}

internal abstract class AbstractConnection(
    config: Config,
    enable: Boolean,
    noPlatform: Boolean?,
    useCQCode: Boolean?,
    retryTimes: Int?,
    retryWaitMills: Long?,
    retryRestMills: Long?,
    heartbeatCheckSeconds: Int?,
    useGroupUploadEventForFileMessage: Boolean?,
    dropEventsBeforeConnected: Boolean?,
) : IConnection {
    override val enabled: Boolean = enable
    private val noLog = config.noLogDoNotReportIfYouSwitchThisOn
    private val noPlatform = noPlatform ?: config.noPlatform
    private val useCQCode = useCQCode ?: config.useCQCode
    private val retryTimes = retryTimes ?: config.retryTimes
    private val retryWaitMills = retryWaitMills ?: config.retryWaitMills
    private val retryRestMills = retryRestMills ?: config.retryRestMills
    private val heartbeatCheckSeconds = heartbeatCheckSeconds ?: config.heartbeatCheckSeconds
    private val useGroupUploadEventForFileMessage = useGroupUploadEventForFileMessage ?: config.useGroupUploadEventForFileMessage
    private val dropEventsBeforeConnected = dropEventsBeforeConnected ?: config.dropEventsBeforeConnected

    override suspend fun connect(
        printInfo: Boolean,
        overrideLogger: Logger?,
        job: Job?
    ): Bot? {
        val builder = botBuilder()

        if (noPlatform) builder.noPlatform()
        if (useCQCode) builder.useCQCode()
        builder.retryTimes(retryTimes)
        builder.retryWaitMills(retryWaitMills)
        builder.retryRestMills(retryRestMills)
        builder.heartbeatCheckSeconds(heartbeatCheckSeconds)
        if (useGroupUploadEventForFileMessage) builder.useGroupUploadEventForFileMessage()
        if (!dropEventsBeforeConnected) builder.keepEventsBeforeConnected()

        if (!printInfo) builder.noPrintInfo()
        if (noLog) {
            val miraiLogger = MiraiLogger.Factory.create(Overflow::class, "Onebot")
            builder.overrideLogger(SLF4JAdapterLogger(miraiLogger))
        } else if (overrideLogger != null) {
            builder.overrideLogger(overrideLogger)
        }

        builder.parentJob(job)

        return builder.connect()
    }

    abstract fun botBuilder(): BotBuilder
}

@Serializable
internal data class ConnWebSocket(
    @SerialName("enable")
    var enable: Boolean,
    @SerialName("host")
    var host: String,
    @SerialName("token")
    var token: String = "",
    // 以下为通用参数
    @SerialName("no_platform")
    var noPlatform: Boolean? = null,
    @SerialName("use_cq_code")
    var useCQCode: Boolean? = null,
    @SerialName("retry_times")
    var retryTimes: Int? = null,
    @SerialName("retry_wait_mills")
    var retryWaitMills: Long? = null,
    @SerialName("retry_rest_mills")
    var retryRestMills: Long? = null,
    @SerialName("heartbeat_check_seconds")
    var heartbeatCheckSeconds: Int? = null,
    @SerialName("use_group_upload_event_for_file_message")
    var useGroupUploadEventForFileMessage: Boolean? = null,
    @SerialName("drop_events_before_connected")
    var dropEventsBeforeConnected: Boolean? = null,
): (Config) -> IConnection {
    override fun invoke(config: Config) = Impl(config, this)

    class Impl(
        config: Config,
        private val conn: ConnWebSocket,
    ) : AbstractConnection(
        config, conn.enable,
        conn.noPlatform,
        conn.useCQCode,
        conn.retryTimes,
        conn.retryWaitMills,
        conn.retryRestMills,
        conn.heartbeatCheckSeconds,
        conn.useGroupUploadEventForFileMessage,
        conn.dropEventsBeforeConnected,
    ) {
        override fun botBuilder(): BotBuilder {
            val builder = BotBuilder.positive(conn.host)
            if (conn.token.isNotBlank()) builder.token(conn.token)
            return builder
        }
    }
}

@Serializable
internal data class ConnWebSocketReverse(
    @SerialName("enable")
    var enable: Boolean,
    @SerialName("port")
    var port: Int,
    @SerialName("token")
    var token: String = "",
    // 以下为通用参数
    @SerialName("no_platform")
    var noPlatform: Boolean? = null,
    @SerialName("use_cq_code")
    var useCQCode: Boolean? = null,
    @SerialName("retry_times")
    var retryTimes: Int? = null,
    @SerialName("retry_wait_mills")
    var retryWaitMills: Long? = null,
    @SerialName("retry_rest_mills")
    var retryRestMills: Long? = null,
    @SerialName("heartbeat_check_seconds")
    var heartbeatCheckSeconds: Int? = null,
    @SerialName("use_group_upload_event_for_file_message")
    var useGroupUploadEventForFileMessage: Boolean? = null,
    @SerialName("drop_events_before_connected")
    var dropEventsBeforeConnected: Boolean? = null,
): (Config) -> IConnection {
    override fun invoke(config: Config) = Impl(config, this)

    class Impl(
        config: Config,
        private val conn: ConnWebSocketReverse,
    ) : AbstractConnection(
        config, conn.enable,
        conn.noPlatform,
        conn.useCQCode,
        conn.retryTimes,
        conn.retryWaitMills,
        conn.retryRestMills,
        conn.heartbeatCheckSeconds,
        conn.useGroupUploadEventForFileMessage,
        conn.dropEventsBeforeConnected,
    ) {
        override fun botBuilder(): BotBuilder {
            val builder = BotBuilder.reversed(conn.port)
            if (conn.token.isNotBlank()) builder.token(conn.token)
            return builder
        }
    }
}
