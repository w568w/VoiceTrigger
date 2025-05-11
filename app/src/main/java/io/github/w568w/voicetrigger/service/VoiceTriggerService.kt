package io.github.w568w.voicetrigger.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.w568w.voicetrigger.Secret.HOME_ASSISTANT_DEVICE
import io.github.w568w.voicetrigger.Secret.HOME_ASSISTANT_TOKEN
import io.github.w568w.voicetrigger.Secret.HOME_ASSISTANT_URL
import io.github.w568w.voicetrigger.util.Chinese
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeAssistantData(@SerialName("entity_id") val entityId: String)

class VoiceTriggerService : AccessibilityService() {

    private var lastResponseTime: Long = 0
    private var lastRecognizingTime: Long = 0
    private val client = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
    }
    private lateinit var tts: TextToSpeech
    private val ttsParam = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(java.util.Locale.CHINA)
                when {
                    result != TextToSpeech.LANG_COUNTRY_AVAILABLE && result != TextToSpeech.LANG_AVAILABLE -> {
                        Log.e(TAG, "Language not supported")
                    }

                    else -> {
                        Log.i(TAG, "TextToSpeech initialized successfully")
                    }
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source ?: return

        val chatTextView = source.findAccessibilityNodeInfosByViewId(CHAT_RESOURCE_ID).firstOrNull()

        val chatText = chatTextView?.let {
            Chinese.cleanUpChineseText(it.text.toString())
        }
        Log.d(TAG, "chatText: $chatText")

        if (chatText == null) {
            lastRecognizingTime = System.currentTimeMillis()
        } else {
            if (System.currentTimeMillis() - lastRecognizingTime < WAIT_SPEAK_TIME_MS) {
                return
            }
        }

        when (chatText) {
            "关灯" -> {
                Log.d(TAG, "Turning off the light")
                if (System.currentTimeMillis() - lastResponseTime < DEBOUNCE_TIME_MS) {
                    return
                }
                lastResponseTime = System.currentTimeMillis()
                performGlobalAction(GLOBAL_ACTION_BACK)
                runBlocking {
                    val resp =
                        client.post("$HOME_ASSISTANT_URL/api/services/light/turn_off") {
                            bearerAuth(HOME_ASSISTANT_TOKEN)
                            contentType(ContentType.Application.Json)
                            setBody(
                                HomeAssistantData(HOME_ASSISTANT_DEVICE)
                            )
                        }
                    Log.i(TAG, "Response: $resp")
                }
                tts.speak("灯已关闭", TextToSpeech.QUEUE_FLUSH, ttsParam, "turn_off")
            }

            "开灯" -> {
                Log.d(TAG, "Turning on the light")
                if (System.currentTimeMillis() - lastResponseTime < DEBOUNCE_TIME_MS) {
                    return
                }
                lastResponseTime = System.currentTimeMillis()
                performGlobalAction(GLOBAL_ACTION_BACK)
                runBlocking {
                    val resp =
                        client.post("$HOME_ASSISTANT_URL/api/services/light/turn_on") {
                            bearerAuth(HOME_ASSISTANT_TOKEN)
                            contentType(ContentType.Application.Json)
                            setBody(
                                HomeAssistantData(HOME_ASSISTANT_DEVICE)
                            )
                        }
                    Log.i(TAG, "Response: $resp")
                }
                tts.speak("灯已打开", TextToSpeech.QUEUE_FLUSH, ttsParam, "turn_on")
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }

    companion object {
        const val TAG = "VoiceTriggerService"
        const val CHAT_RESOURCE_ID = "com.vivo.ai.copilot:id/ask_text_view"
        const val DEBOUNCE_TIME_MS = 3000L // 3 seconds
        const val WAIT_SPEAK_TIME_MS = 2000L // 2 seconds
    }

}