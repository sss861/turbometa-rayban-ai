package com.tourmeta.app.services

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tourmeta.app.managers.AlibabaEndpoint
import com.tourmeta.app.managers.LiveAIModeManager
import com.tourmeta.app.utils.APIKeyManager
import com.tourmeta.app.utils.AgeGroup
import com.tourmeta.app.utils.GuideStyle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Alibaba Qwen Omni Realtime Service
 * Supports multi-region endpoints (Beijing/Singapore)
 * 1:1 port from iOS OmniRealtimeService.swift
 */
class OmniRealtimeService(
    private val apiKey: String,
    private val model: String = "qwen3-omni-flash-realtime",
    private val outputLanguage: String = "zh-CN",
    private val endpoint: AlibabaEndpoint = AlibabaEndpoint.BEIJING,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "OmniRealtimeService"
        private const val WS_BEIJING_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
        private const val WS_SINGAPORE_URL = "wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val websocketURL: String
        get() = when (endpoint) {
            AlibabaEndpoint.BEIJING -> WS_BEIJING_URL
            AlibabaEndpoint.SINGAPORE -> WS_SINGAPORE_URL
        }

    // State
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Callbacks
    var onTranscriptDelta: ((String) -> Unit)? = null
    var onTranscriptDone: ((String) -> Unit)? = null
    var onUserTranscript: ((String) -> Unit)? = null
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechStopped: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // Internal
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private val audioQueue = mutableListOf<ByteArray>()
    private val gson = Gson()
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pendingImageFrame: Bitmap? = null
    private var lastImageSentTime = 0L
    private val imageSendIntervalMs = 500L  // 发送图片的间隔（毫秒）

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        if (_isConnected.value) return

        // Reset scope if it was cancelled (after previous disconnect)
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            Log.d(TAG, "Scope was cancelled, created new scope")
        }

        val url = "$websocketURL?model=$model"
        Log.d(TAG, "Connecting to endpoint: ${endpoint.displayName}")
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _isConnected.value = true
                sendSessionUpdate()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _isConnected.value = false
                _errorMessage.value = t.message
                onError?.invoke(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        stopRecording()
        stopAudioPlayback()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        _isRecording.value = false
        _isSpeaking.value = false
        scope.cancel()
    }

    fun startRecording() {
        if (_isRecording.value) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            lastImageSentTime = 0  // 重置，确保立即发送第一张图片

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        sendAudioData(buffer.copyOf(bytesRead))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied")
            _errorMessage.value = "Microphone permission denied"
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            _errorMessage.value = e.message
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun updateVideoFrame(frame: Bitmap) {
        pendingImageFrame = frame
    }

    private fun sendSessionUpdate() {
        // Use mode manager if context is available, otherwise fall back to language-based prompt
        val instructions = context?.let {
            val modeManager = LiveAIModeManager.getInstance(it)
            val api = APIKeyManager.getInstance(it)
            val ageGroup = api.getVisitorAgeGroup()
            val style = api.getGuideStyle()
            val base = modeManager.getSystemPrompt()
            enhancePromptWithPreferences(base, ageGroup, style, outputLanguage)
        } ?: getLiveAIPrompt(outputLanguage)

        val sessionConfig = mapOf(
            "type" to "session.update",
            "session" to mapOf(
                "modalities" to listOf("text", "audio"),
                "voice" to "Cherry",
                "input_audio_format" to "pcm16",
                "output_audio_format" to "pcm16",  // PCM16 works better with Android AudioTrack
                "smooth_output" to true,
                "instructions" to instructions,
                "turn_detection" to mapOf(
                    "type" to "server_vad",
                    "threshold" to 0.5,
                    "silence_duration_ms" to 800
                )
            )
        )

        val json = gson.toJson(sessionConfig)
        webSocket?.send(json)
    }

    /**
     * Get localized Live AI prompt matching iOS implementation
     */
    private fun getLiveAIPrompt(language: String): String {
        return when (language) {
            "zh-CN" -> """
    你是一位专业的博物馆资深导览员。
    
    【重要】用户目前正佩戴着 Ray-Ban Meta 智能眼镜参观博物馆。
    1. 你的职责是识别眼镜拍摄到的展品、文物或艺术品。
    2. 请详细且生动地讲解它们的历史背景、艺术特色和文化内涵。
    3. 必须始终用中文回答，语气要亲切、专业，像在现场为游客一对一讲解一样。
    4. 回答要重点突出，引导用户观察展品的细节。
""".trimIndent()
            "zh-HK" -> """
    你是一位專業的博物館資深導覽員。
    
    【重要】用戶目前正佩戴 Ray-Ban Meta 智能眼鏡參觀博物館。
    1. 你的職責是識別眼鏡拍攝到的展品、文物或藝術品。
    2. 請詳細且生動地講解它們的歷史背景、藝術特色與文化內涵。
    3. 必須始終用繁體中文回答，語氣要親切、專業，像在現場為遊客一對一講解。
    4. 回答要重點突出，引導用戶觀察展品細節。
""".trimIndent()

            "en-US" -> """
    You are a professional museum tour guide.

    [IMPORTANT] The user is currently wearing Ray-Ban Meta smart glasses while visiting a museum.
    1. Your role is to identify exhibits, artifacts, or artworks captured by the glasses.
    2. Provide detailed and engaging explanations about their historical background, artistic features, and cultural significance.
    3. Always respond in English with a friendly and professional tone, as if providing a private tour.
    4. Keep explanations insightful and guide the user to notice specific details of the exhibits.
""".trimIndent()

            "ja-JP" -> """
    あなたはプロの博物館ガイドです。

    【重要】ユーザーは現在、Ray-Ban Metaスマートグラスを着用して博物館を見学しています。
    1. あなたの役割は、眼鏡が捉えた展示品、遺物、または芸術品を特定することです。
    2. それらの歴史的背景、芸術的特徴、文化的意義について、詳しく魅力的に説明してください。
    3. 常に日本語で、親しみやすくプロフェッショナルなトーンで回答してください。
    4. 解説は要点を絞り、展示品の細部に注目するようユーザーを誘導してください。
""".trimIndent()

            "ko-KR" -> """
    당신은 전문적인 박물관 도슨트입니다.

    【중요】사용자는 현재 Ray-Ban Meta 스마트 안경을 착용하고 박물관을 관람 중입니다.
    1. 당신의 역할은 안경에 포착된 전시물, 유물 또는 예술품을 식별하는 것입니다.
    2. 해당 전시물의 역사적 배경, 예술적 특징 및 문화적 가치에 대해 상세하고 흥미롭게 설명해 주세요.
    3. 항상 한국어로 응답하며, 현장에서 직접 안내하는 것처럼 친절하고 전문적인 어조를 유지하세요.
    4. 핵심 위주로 설명하되, 사용자가 전시물의 세부 사항을 관찰할 수 있도록 안내하세요.
""".trimIndent()
            else -> getLiveAIPrompt("en-US")
        }
    }

    private fun enhancePromptWithPreferences(
        basePrompt: String,
        ageGroup: AgeGroup,
        style: GuideStyle,
        language: String
    ): String {
        val styleInstruction = when (style) {
            GuideStyle.CONCISE -> when (language) {
                "zh-CN" -> "请用简洁直观的方式讲解，突出关键点和直观观察。"
                "zh-HK" -> "請用簡潔直觀的方式講解，突出關鍵點與直觀觀察。"
                else -> "Keep explanations concise and direct, highlighting key points."
            }
            GuideStyle.STORYTELLING -> when (language) {
                "zh-CN" -> "请采用讲故事的形式，关联历史人物与时代背景，增强沉浸感。"
                "zh-HK" -> "請採用講故事的形式，關聯歷史人物與時代背景，增強沉浸感。"
                else -> "Use storytelling, relate historical figures and context to engage."
            }
            GuideStyle.ACADEMIC -> when (language) {
                "zh-CN" -> "请采用学术风格，增加术语与学术来源，但保持可理解。"
                "zh-HK" -> "請採用學術風格，增加術語與學術來源，但保持可理解。"
                else -> "Adopt an academic tone with terminology and sources, remain clear."
            }
        }

        val ageInstruction = when (ageGroup) {
            AgeGroup.CHILD_UNDER_12 -> when (language) {
                "zh-CN" -> "面向12岁以下，请使用通俗易懂、富有趣味的语言，加入类比与互动提问。"
                "zh-HK" -> "面向12歲以下，請使用通俗易懂、富有趣味的語言，加入類比與互動提問。"
                else -> "For under 12, use simple, fun language with analogies and questions."
            }
            AgeGroup.TEEN_12_18 -> when (language) {
                "zh-CN" -> "面向12-18岁，请增强互动与探索感，兼顾知识性与趣味性。"
                "zh-HK" -> "面向12–18歲，請增強互動與探索感，兼顧知識性與趣味性。"
                else -> "For 12–18, keep interactive and exploratory, balancing facts and fun."
            }
            AgeGroup.ADULT_18_30 -> when (language) {
                "zh-CN" -> "面向18-30岁，请深入但不冗长，强调现代关联与跨学科联系。"
                "zh-HK" -> "面向18–30歲，請深入但不冗長，強調現代關聯與跨學科聯繫。"
                else -> "For 18–30, be insightful but concise; relate to modern, cross-disciplinary links."
            }
            AgeGroup.ADULT_30_50 -> when (language) {
                "zh-CN" -> "面向30-50岁，请保持结构清晰，适度深入历史与工艺细节。"
                "zh-HK" -> "面向30–50歲，請保持結構清晰，適度深入歷史與工藝細節。"
                else -> "For 30–50, clear structure with moderate depth into history and craft."
            }
            AgeGroup.SENIOR_OVER_50 -> when (language) {
                "zh-CN" -> "面向50岁以上，请语速适中、重点清晰，多做阶段性小结。"
                "zh-HK" -> "面向50歲以上，請語速適中、重點清晰，多做階段性小結。"
                else -> "For 50+, moderate pace, clear highlights, include brief summaries."
            }
        }

        return listOf(basePrompt.trim(), styleInstruction, ageInstruction).joinToString("\n\n")
    }

    private fun sendAudioData(audioData: ByteArray) {
        if (!_isConnected.value) return

        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = mapOf(
            "type" to "input_audio_buffer.append",
            "audio" to base64Audio
        )

        webSocket?.send(gson.toJson(message))

        // 定期发送图片（每 500ms 发送一次）
        val currentTime = System.currentTimeMillis()
        if (pendingImageFrame != null && (currentTime - lastImageSentTime >= imageSendIntervalMs)) {
            lastImageSentTime = currentTime
            sendImageFrame(pendingImageFrame!!)
        }
    }

    private fun sendImageFrame(bitmap: Bitmap) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val bytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val message = mapOf(
                "type" to "input_image_buffer.append",
                "image" to base64Image
            )

            webSocket?.send(gson.toJson(message))
            Log.d(TAG, "Image frame sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image: ${e.message}")
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "session.created", "session.updated" -> {
                    Log.d(TAG, "Session ready")
                }
                "input_audio_buffer.speech_started" -> {
                    _isSpeaking.value = false
                    stopAudioPlayback()
                    onSpeechStarted?.invoke()
                }
                "input_audio_buffer.speech_stopped" -> {
                    onSpeechStopped?.invoke()
                }
                "response.audio_transcript.delta" -> {
                    val delta = json.get("delta")?.asString ?: ""
                    _currentTranscript.value += delta
                    onTranscriptDelta?.invoke(delta)
                }
                "response.audio_transcript.done" -> {
                    val transcript = _currentTranscript.value
                    onTranscriptDone?.invoke(transcript)
                    _currentTranscript.value = ""
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.get("transcript")?.asString ?: ""
                    onUserTranscript?.invoke(transcript)
                }
                "response.audio.delta" -> {
                    val audioData = json.get("delta")?.asString ?: return
                    val audioBytes = Base64.decode(audioData, Base64.DEFAULT)
                    playAudio(audioBytes)
                }
                "response.audio.done" -> {
                    _isSpeaking.value = false
                }
                "error" -> {
                    val errorMsg = json.get("error")?.asJsonObject?.get("message")?.asString
                    Log.e(TAG, "Server error: $errorMsg")
                    _errorMessage.value = errorMsg
                    onError?.invoke(errorMsg ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    private fun playAudio(audioData: ByteArray) {
        synchronized(audioQueue) {
            audioQueue.add(audioData)
        }

        if (audioPlaybackJob?.isActive != true) {
            startAudioPlayback()
        }
    }

    private fun startAudioPlayback() {
        if (audioTrack == null) {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // 使用 AudioAttributes 替代已弃用的 STREAM_MUSIC（兼容性更好）
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        }

        _isSpeaking.value = true

        audioPlaybackJob = scope.launch {
            while (isActive) {
                val data = synchronized(audioQueue) {
                    if (audioQueue.isNotEmpty()) audioQueue.removeAt(0) else null
                }

                if (data != null) {
                    // Directly write PCM16 data - no conversion needed
                    audioTrack?.write(data, 0, data.size)
                } else {
                    delay(10)
                    // Check if queue is still empty
                    val isEmpty = synchronized(audioQueue) { audioQueue.isEmpty() }
                    if (isEmpty) {
                        delay(100)
                        val stillEmpty = synchronized(audioQueue) { audioQueue.isEmpty() }
                        if (stillEmpty) {
                            _isSpeaking.value = false
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopAudioPlayback() {
        audioPlaybackJob?.cancel()
        synchronized(audioQueue) {
            audioQueue.clear()
        }
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _isSpeaking.value = false
    }

    private fun convertPcm24ToPcm16(pcm24Data: ByteArray): ByteArray {
        // PCM24 is 3 bytes per sample, PCM16 is 2 bytes per sample
        // We need to convert by taking the upper 16 bits of each 24-bit sample
        val sampleCount = pcm24Data.size / 3
        val pcm16Data = ByteArray(sampleCount * 2)
        val buffer = ByteBuffer.wrap(pcm24Data).order(ByteOrder.LITTLE_ENDIAN)
        val outBuffer = ByteBuffer.wrap(pcm16Data).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val sample24 = buffer.get().toInt() and 0xFF or
                    ((buffer.get().toInt() and 0xFF) shl 8) or
                    ((buffer.get().toInt() and 0xFF) shl 16)

            // Sign extend if negative
            val signedSample = if (sample24 and 0x800000 != 0) {
                sample24 or 0xFF000000.toInt()
            } else {
                sample24
            }

            // Take upper 16 bits
            val sample16 = (signedSample shr 8).toShort()
            outBuffer.putShort(sample16)
        }

        return pcm16Data
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
