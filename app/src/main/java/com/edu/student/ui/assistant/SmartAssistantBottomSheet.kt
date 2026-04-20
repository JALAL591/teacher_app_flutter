package com.edu.student.ui.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edu.student.ai.SmartAssistant
import com.edu.teacher.databinding.BottomSheetSmartAssistantBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SmartAssistantBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetSmartAssistantBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var smartAssistant: SmartAssistant
    private var lessonContent: String = ""
    private var lessonTitle: String = ""
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceInput()
        } else {
            Toast.makeText(context, "إذن الميكروفون مطلوب للإملاء الصوتي", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSmartAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        smartAssistant.initialize()
        
        setupViews()
        observeState()
        
        if (lessonContent.isNotEmpty()) {
            smartAssistant.setLessonContext(lessonTitle, lessonContent)
        }
    }
    
    private fun setupViews() {
        binding.sendButton.setOnClickListener {
            val question = binding.questionInput.text.toString().trim()
            if (question.isNotEmpty()) {
                askQuestion(question)
            }
        }
        
        binding.microphoneButton.setOnClickListener {
            checkMicrophonePermission()
        }
        
        binding.readAloudButton.setOnClickListener {
            smartAssistant.readLessonAloud()
            Toast.makeText(context, "جاري قراءة الدرس...", Toast.LENGTH_SHORT).show()
        }
        
        binding.summarizeButton.setOnClickListener {
            summarizeLesson()
        }
        
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            smartAssistant.isSpeaking.collectLatest { isSpeaking ->
                if (isSpeaking) {
                    binding.readAloudButton.text = "⏹ إيقاف"
                    binding.readAloudButton.setOnClickListener {
                        smartAssistant.stopReading()
                    }
                } else {
                    binding.readAloudButton.text = "📖 قراءة الدرس"
                    binding.readAloudButton.setOnClickListener {
                        smartAssistant.readLessonAloud()
                    }
                }
            }
        }
    }
    
    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceInput()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(context, "إذن الميكروفون مطلوب للإملاء الصوتي", Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun startVoiceInput() {
        Toast.makeText(context, "🎤 جاري الإصغاء...", Toast.LENGTH_SHORT).show()
    }
    
    private fun askQuestion(question: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.sendButton.isEnabled = false
        
        binding.questionInput.text?.clear()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val answer = smartAssistant.askQuestion(question)
                
                binding.responseText.append("\n\n📝 س: $question\n")
                binding.responseText.append("🤖 ج: $answer\n")
                
                binding.responseScrollView.post {
                    binding.responseScrollView.fullScroll(View.FOCUS_DOWN)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.sendButton.isEnabled = true
            }
        }
    }
    
    private fun summarizeLesson() {
        binding.progressBar.visibility = View.VISIBLE
        binding.summarizeButton.isEnabled = false
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = smartAssistant.summarizeLesson()
                
                binding.responseText.append("\n\n📋 ملخص الدرس:\n$summary\n")
                
                binding.responseScrollView.post {
                    binding.responseScrollView.fullScroll(View.FOCUS_DOWN)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في التلخيص: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.summarizeButton.isEnabled = true
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        smartAssistant.cleanup()
        _binding = null
    }
    
    companion object {
        fun show(
            context: Context,
            smartAssistant: SmartAssistant,
            lessonTitle: String = "",
            lessonContent: String = "",
            pdfExtractedText: String = ""
        ) {
            smartAssistant.setLessonContext(lessonTitle, lessonContent)
            if (pdfExtractedText.isNotEmpty()) {
                smartAssistant.setPdfExtractedText(pdfExtractedText)
            }
            
            val bottomSheet = SmartAssistantBottomSheet().apply {
                this.lessonTitle = lessonTitle
                this.lessonContent = lessonContent
                this.smartAssistant = smartAssistant
            }
            
            (context as? AppCompatActivity)?.let {
                bottomSheet.show(it.supportFragmentManager, "SmartAssistantBottomSheet")
            }
        }
    }
}