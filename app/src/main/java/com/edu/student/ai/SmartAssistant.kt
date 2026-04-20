package com.edu.student.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.edu.student.utils.PdfTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

class SmartAssistant(private val context: Context) {
    
    companion object {
        private const val TAG = "SmartAssistant"
    }
    
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var lessonContext: String = ""
    private var lessonTitle: String = ""
    private var pdfExtractedText: String = ""
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    
    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun initialize() {
        scope.launch(Dispatchers.Main) {
            initTTS()
            _isReady.value = isTtsInitialized
            Log.d(TAG, "Assistant initialized: ${_isReady.value}")
        }
    }
    
    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ar"))
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = true
                    Log.d(TAG, "TTS initialized")
                    
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                        
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
            }
        }
    }
    
    fun setLessonContext(title: String, text: String) {
        lessonTitle = title
        lessonContext = text
        _extractedText.value = text
        Log.d(TAG, "Lesson context set: ${text.length} characters")
    }
    
    fun setPdfExtractedText(text: String) {
        pdfExtractedText = text
        Log.e(TAG, "PDF text set: ${text.length} chars")
    }
    
    private fun getFullContext(): String {
        return buildString {
            if (lessonContext.isNotEmpty()) {
                append("محتوى الدرس:\n")
                append(lessonContext)
                append("\n\n")
            }
            if (pdfExtractedText.isNotEmpty()) {
                append("نص PDF المستخرج:\n")
                append(pdfExtractedText)
                append("\n\n")
            }
        }
    }
    
    suspend fun loadLessonFromPdf(pdfFile: File, title: String): String = withContext(Dispatchers.IO) {
        _extractedText.value = ""
        
        Log.e(TAG, "Loading PDF: ${pdfFile.absolutePath}")
        
        val text = PdfTextExtractor.extractTextFromPdf(pdfFile) { current, total ->
            Log.e(TAG, "PDF progress: $current/$total")
        }
        
        if (text.isNotEmpty()) {
            setLessonContext(title, text)
            Log.e(TAG, "PDF loaded: ${text.length} chars")
        } else {
            Log.e(TAG, "PDF extraction failed - no text")
        }
        
        text
    }
    
    suspend fun loadLessonFromBase64Pdf(base64Pdf: String, title: String): String = withContext(Dispatchers.IO) {
        val text = PdfTextExtractor.extractTextFromBase64Pdf(base64Pdf)
        setLessonContext(title, text)
        text
    }
    
    fun readLessonAloud() {
        if (lessonContext.isEmpty()) return
        stopReading()
        tts?.speak(lessonContext, TextToSpeech.QUEUE_FLUSH, null, "lesson_${UUID.randomUUID()}")
    }
    
    fun readTextAloud(text: String) {
        if (text.isEmpty()) return
        stopReading()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "text_${UUID.randomUUID()}")
    }
    
    fun stopReading() {
        tts?.stop()
        _isSpeaking.value = false
    }
    
    suspend fun askQuestion(question: String): String = withContext(Dispatchers.IO) {
        try {
            val fullContext = getFullContext()
            Log.e(TAG, "Full context size: ${fullContext.length} chars")
            
            val prompt = buildString {
                append("أنت مساعد تعليمي ذكي. أجب عن السؤال التالي بناءً على المحتوى المتاح.\n\n")
                if (fullContext.isNotEmpty()) {
                    append("المحتوى:\n$fullContext\n\n")
                }
                append("سؤال الطالب: $question\n\n")
                append("أجب بشكل مختصر ومفيد:")
            }
            
            generateAIResponse(prompt, question)
        } catch (e: Exception) {
            Log.e(TAG, "Question failed: ${e.message}")
            "عذراً، لم أستطع الإجابة على سؤالك. حاول مرة أخرى."
        }
    }
    
    suspend fun summarizeLesson(): String = withContext(Dispatchers.IO) {
        if (lessonContext.isEmpty()) {
            return@withContext "لا يوجد درس لتلخيصه"
        }
        
        summarizeText(lessonContext)
    }
    
    private fun generateAIResponse(context: String, question: String): String {
        val q = question.lowercase()
        
        return when {
            q.contains("ما معنى") || q.contains("تعريف") -> {
                findDefinitionInContext(context, question)
            }
            q.contains("لخص") || q.contains("ملخص") -> {
                summarizeText(context)
            }
            q.contains("اشرح") || q.contains("وضح") -> {
                explainConcept(context, question)
            }
            q.contains("الفرق") || q.contains("مقارنة") -> {
                compareConcepts(context, question)
            }
            q.contains("متى") || q.contains("تاريخ") -> {
                findDateOrEvent(context, question)
            }
            q.contains("لماذا") || q.contains("سبب") -> {
                findReason(context, question)
            }
            else -> {
                generateGeneralResponse(context, question)
            }
        }
    }
    
    private fun findDefinitionInContext(text: String, question: String): String {
        val sentences = text.split(".").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (sentence in sentences) {
            if (sentence.contains(question.replace("ما معنى", "").replace("تعريف", "").trim())) {
                return sentence
            }
        }
        
        return "بناءً على محتوى الدرس، يمكن القول أن ${question.replace("ما معنى", "").replace("تعريف", "").trim()} يعني..."
    }
    
    private fun summarizeText(text: String): String {
        val sentences = text.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        
        if (sentences.size <= 5) {
            return text
        }
        
        val importantPoints = sentences.take(5)
        
        return buildString {
            append("📝 ملخص الدرس:\n\n")
            importantPoints.forEachIndexed { index, point ->
                append("${index + 1}. ${point.trim()}\n\n")
            }
        }
    }
    
    private fun explainConcept(text: String, question: String): String {
        val concept = question.replace("اشرح", "").replace("وضح", "").trim()
        
        val sentences = text.split(".").map { it.trim() }.filter { it.isNotEmpty() }
        
        val related = sentences.filter { it.contains(concept) }
        
        return if (related.isNotEmpty()) {
            buildString {
                append("💡 شرح مفهوم $concept:\n\n")
                append(related.take(3).joinToString(".\n"))
                append(".")
            }
        } else {
            "لم أجد شرحاً محدداً لـ $concept في الدرس. هل تريد سؤالاً أكثر تحديداً؟"
        }
    }
    
    private fun compareConcepts(text: String, question: String): String {
        val parts = question.replace("الفرق", "").replace("مقارنة", "").split("و").map { it.trim() }
        
        if (parts.size < 2) {
            return "لم أتمكن من المقارنة. الرجاء تحديد عنصرين للمقارنة."
        }
        
        return buildString {
            append("🔄 مقارنة بين ${parts[0]} و ${parts[1]}:\n\n")
            append("• ${parts[0]}: يُستخدم في...\n")
            append("• ${parts[1]}: يُستخدم في...\n")
            append("• الفرق الأساسي: ")
        }
    }
    
    private fun findDateOrEvent(text: String, question: String): String {
        val datePattern = Regex("\\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}|القرن \\d+|العصر \\d+")
        val matches = datePattern.findAll(text).map { it.value }.toList()
        
        return if (matches.isNotEmpty()) {
            "تاريخ هذا الموضوع: ${matches.first()}"
        } else {
            "لم أجد تاريخاً محدداً في الدرس."
        }
    }
    
    private fun findReason(text: String, question: String): String {
        val becausePattern = Regex("(لذلك|لأن|原因是|故|是因为)")
        val matches = becausePattern.findAll(text).toList()
        
        return if (matches.isNotEmpty()) {
            "السبب: ${matches.first().value}..."
        } else {
            "السبب الرئيسي يعود إلى..."
        }
    }
    
    private fun generateGeneralResponse(context: String, question: String): String {
        val q = question.lowercase()
        
        return when {
            q.contains("مرحبا") || q.contains("السلام") -> {
                "👋 مرحباً! أنا مساعدك الذكي. كيف يمكنني مساعدتك؟"
            }
            q.contains("شكرا") || q.contains("شكراً") -> {
                "عفواً! سعيد بمساعدتك. 🚀"
            }
            q.contains("مساعدة") || q.contains("/help") -> {
                "📚 يمكنني مساعدتك في:\n• الإجابة على أسئلتك حول الدرس\n• تلخيص المحتوى\n• شرح المفاهيم\n\nما الذي تريد معرفته؟"
            }
            q.contains("مفيد") -> {
                "😊 سعيد بأنك وجدت المحتوى مفيداً! teruskan belajar!"
            }
            else -> {
                if (context.length > 100) {
                    "بناءً على الدرس: $question\n\nالإجابة: ${context.take(200)}..."
                } else {
                    "أفهم سؤالك. $question\n\nهل يمكنك إعادة صياغة السؤال更为 jelas؟"
                }
            }
        }
    }
    
    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}