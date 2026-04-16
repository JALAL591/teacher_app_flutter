package com.edu.student.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Question

class QuestionAdapter(
    private val questions: List<Question>,
    private val repository: StudentRepository,
    private val lessonId: String
) : RecyclerView.Adapter<QuestionAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StudentItemQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(questions[position], position)
    }
    
    override fun getItemCount() = questions.size
    
    inner class ViewHolder(private val binding: StudentItemQuestionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(question: Question, position: Int) {
            binding.questionNumber.text = (position + 1).toString()
            binding.questionText.text = question.text
            
            when (question.type) {
                "boolean" -> setupBooleanQuestion(question)
                "choice" -> setupChoiceQuestion(question)
                else -> setupEssayQuestion()
            }
        }
        
        private fun setupBooleanQuestion(question: Question) {
            binding.mcqOptions.visibility = View.GONE
            binding.booleanOptions.visibility = View.VISIBLE
            binding.essayHint.visibility = View.GONE
            
            binding.trueButton.setOnClickListener {
                checkAnswer(question, binding.trueButton.text.toString(), question.answer)
            }
            
            binding.falseButton.setOnClickListener {
                checkAnswer(question, binding.falseButton.text.toString(), question.answer)
            }
        }
        
        private fun setupChoiceQuestion(question: Question) {
            binding.mcqOptions.visibility = View.VISIBLE
            binding.booleanOptions.visibility = View.GONE
            binding.essayHint.visibility = View.GONE
            
            val options = question.options.ifEmpty { listOf("الخيار الأول", "الخيار الثاني") }
            
            binding.option1.text = options.getOrElse(0) { "الخيار الأول" }
            binding.option2.text = options.getOrElse(1) { "الخيار الثاني" }
            
            binding.option1.setOnClickListener {
                checkAnswer(question, "1", question.answer)
            }
            
            binding.option2.setOnClickListener {
                checkAnswer(question, "2", question.answer)
            }
        }
        
        private fun setupEssayQuestion() {
            binding.mcqOptions.visibility = View.GONE
            binding.booleanOptions.visibility = View.GONE
            binding.essayHint.visibility = View.VISIBLE
            binding.essayHint.text = "📝 اكتب إجابتك على ورقة ثم التقط صورة وأرسلها للمعلم"
        }
        
        private fun checkAnswer(question: Question, selected: String, correct: String) {
            val isCorrect = selected.trim().equals(correct.trim(), ignoreCase = true)
            
            if (isCorrect) {
                repository.addPoints(lessonId, "q-${question.id}", 5)
                Toast.makeText(binding.root.context, "إجابة صحيحة! +5 🌟", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(binding.root.context, "حاول مرة أخرى! 💪", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
