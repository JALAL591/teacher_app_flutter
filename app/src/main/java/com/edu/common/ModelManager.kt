package com.edu.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    
    private const val TAG = "ModelManager"
    private const val MODELS_DIR = "models"
    const val WHISPER_MODEL = "ggml-tiny.bin"
    const val LLM_MODEL = "model.gguf"
    
    fun getModelsDir(context: Context): File {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }
    
    fun copyModelsFromAssets(context: Context) {
        val modelsDir = getModelsDir(context)
        val models = listOf(WHISPER_MODEL, LLM_MODEL)
        
        for (model in models) {
            val destFile = File(modelsDir, model)
            if (!destFile.exists()) {
                try {
                    context.assets.open("models/$model").use { input ->
                        FileOutputStream(destFile).use { output ->
                            val buffer = ByteArray(8192)
                            var length: Int
                            var totalCopied = 0L
                            
                            while (input.read(buffer).also { length = it } > 0) {
                                output.write(buffer, 0, length)
                                totalCopied += length
                            }
                            
                            Log.d(TAG, "Copied $model (${totalCopied / 1024 / 1024} MB)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $model: ${e.message}")
                }
            } else {
                Log.d(TAG, "$model already exists")
            }
        }
    }
    
    suspend fun copyModelsAsync(context: Context, onProgress: (String, Int) -> Unit = { _, _ -> }) {
        withContext(Dispatchers.IO) {
            val modelsDir = getModelsDir(context)
            val models = listOf(WHISPER_MODEL, LLM_MODEL)
            
            models.forEachIndexed { index, model ->
                val destFile = File(modelsDir, model)
                if (!destFile.exists()) {
                    onProgress(model, 0)
                    try {
                        context.assets.open("models/$model").use { input ->
                            FileOutputStream(destFile).use { output ->
                                val buffer = ByteArray(8192)
                                var length: Int
                                var totalCopied = 0L
                                
                                while (input.read(buffer).also { length = it } > 0) {
                                    output.write(buffer, 0, length)
                                    totalCopied += length
                                }
                                
                                Log.d(TAG, "Copied $model (${totalCopied / 1024 / 1024} MB)")
                            }
                        }
                        onProgress(model, 100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy $model: ${e.message}")
                        onProgress(model, -1)
                    }
                }
            }
        }
    }
    
    fun hasAllModels(context: Context): Boolean {
        val modelsDir = getModelsDir(context)
        return File(modelsDir, WHISPER_MODEL).exists() && 
               File(modelsDir, LLM_MODEL).exists()
    }
    
    fun hasWhisperModel(context: Context): Boolean {
        return File(getModelsDir(context), WHISPER_MODEL).exists()
    }
    
    fun hasLLMModel(context: Context): Boolean {
        return File(getModelsDir(context), LLM_MODEL).exists()
    }
    
    fun getWhisperModelPath(context: Context): String {
        return File(getModelsDir(context), WHISPER_MODEL).absolutePath
    }
    
    fun getLLMModelPath(context: Context): String {
        return File(getModelsDir(context), LLM_MODEL).absolutePath
    }
    
    fun getModelsSize(context: Context): Long {
        var totalSize = 0L
        val modelsDir = getModelsDir(context)
        modelsDir.listFiles()?.forEach { file ->
            totalSize += file.length()
        }
        return totalSize
    }
    
    fun logModelsInfo(context: Context) {
        val modelsDir = getModelsDir(context)
        Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")
        Log.d(TAG, "Whisper model exists: ${hasWhisperModel(context)}")
        Log.d(TAG, "LLM model exists: ${hasLLMModel(context)}")
        Log.d(TAG, "Total size: ${getModelsSize(context) / 1024 / 1024} MB")
        
        modelsDir.listFiles()?.forEach { file ->
            Log.d(TAG, "File: ${file.name} (${file.length() / 1024 / 1024} MB)")
        }
    }
}