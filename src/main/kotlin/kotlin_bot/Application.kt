package kotlin_bot

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.int
import com.beust.klaxon.obj
import com.beust.klaxon.string
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import java.util.*
import java.util.logging.Logger

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter())
    }
    install(StatusPages) {
        exception<BadRequestException> {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    val logger = Logger.getLogger("applog")

    val configList: List<SecretConfig> = jacksonObjectMapper().readValue(
            ClassLoader.getSystemClassLoader().getResource("secret.json")
    )
    val secretConfig = configList.map{it.teamId to it}.toMap()
    logger.info(secretConfig.toString())

    install(Routing) {
        get("/") {
            call.respondText("I am Botlin!!")
        }
        post("/") {
            val payload = try {
                call.receive<LinkedHashMap<String, Any>>()
            } catch (e: Exception) {
                throw BadRequestException()
            }
            val type = payload["type"] as String? ?: ""
            val teamId = payload["team_id"] as String?
            val token = secretConfig[teamId]?.token

            logger.info("payload: ${payload}")
            logger.info("type: ${type}")

            if(token.isNullOrEmpty()) {
                logger.warning("token is null: [teamId: ${teamId}]")
                call.respond(status = HttpStatusCode.OK, message = "")
            } else {
                when (type) {
                    "url_verification" -> {
                        call.respondText(
                                contentType = ContentType.Text.Plain,
                                status = HttpStatusCode.OK,
                                text = payload["challenge"] as String? ?: ""
                        )
                    }
                    "event_callback" -> {
                        val eventObj = payload["event"] as LinkedHashMap<String, String>?
                        logger.info("eventObj: ${eventObj}")
                        if (eventObj != null) {
                            val text = eventObj["text"] ?: ""
                            when (eventObj["type"]) {
                                "message" -> {
                                    var message = ""
                                    var attachements = ""
                                    var username = ""

                                    when {
                                        text.startsWith("<@${secretConfig[teamId]?.botuser}>") -> {
                                            when {
                                                text.matches(".*名前.*".toRegex()) -> {
                                                    message = "Botlinと言います"
                                                }
                                                text.matches(".*生年月日.*".toRegex()) -> {
                                                    message = "Kotlinが最初に発表されたのは2011年7月20日です"
                                                }
                                                text.matches(".*技術スタック.*".toRegex()) -> {
                                                    message = """
・言語：Kotlin
・フレームワーク：Ktor
・実行環境：Google Apps Engine [Flexible enviroment]
"""
                                                }
                                                else -> {
                                                    message = "呼びましたか？"
                                                    attachements = text
                                                }
                                            }
                                        }
                                        text.toLowerCase().indexOf("botlin") > 0 -> {
                                            message = "私です"
                                        }
                                        text.matches(".*友達なら.*".toRegex()) -> {
                                            message = "アタリマエ〜"
                                            username = "アルシンド"
                                        }
                                    }

                                    if (message.isNotEmpty()) {
                                        val map = mutableMapOf<String, Any?>()
                                        map.put("token", token)
                                        map.put("channel", eventObj["channel"])
                                        map.put("text", message)
                                        if (username.isNotEmpty()) {
                                            map.put("username", username)
                                        }
                                        if (attachements.isNotEmpty()) {
                                            map.put("attachments", JsonArray(JsonObject(mapOf(
                                                    "color" to "#42ce9f",
                                                    "fields" to JsonArray(JsonObject(mapOf(
                                                            "value" to attachements,
                                                            "short" to true
                                                    )))
                                            ))).toJsonString())
                                        }

                                        val (_, _, _) = "https://slack.com/api/chat.postMessage".httpPost(map.toList()).responseString()
                                    }
                                }
                            }
                        }
                        call.respond(status = HttpStatusCode.OK, message = "")
                    }
                    else -> {
                        logger.info("payload type is [${type}]")
                        call.respond(status = HttpStatusCode.OK, message = "")
                    }
                }
            }
        }
    }
}

data class SecretConfig(val teamId: String, val token: String, val botuser: String)

class BadRequestException: RuntimeException()
