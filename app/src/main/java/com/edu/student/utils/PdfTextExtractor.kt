package com.edu.student.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfTextExtractor {
    
    private const val TAG = "PdfTextExtractor"
    
    private var tessApi: TessBaseAPI? = null
    private var isTesseractInitialized = false
    
    fun initTesseract(context: Context) {
        if (isTesseractInitialized) return
        
        try {
            PDFBoxResourceLoader.init(context)
            
            val tessApiInstance = TessBaseAPI()
            val dataPath = File(context.filesDir, "tesseract").absolutePath
            
            copyTessDataIfNeeded(context, dataPath)
            
            val initialized = tessApiInstance.init(dataPath, "ara")
            if (initialized) {
                tessApiInstance.setVariable("tessedit_ocr_engine_mode", "1")
                tessApiInstance.setVariable("load_system_dawg", "false")
                tessApiInstance.setVariable("load_freq_dawg", "false")
                tessApiInstance.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ابتثجحخدذرزسشصضطظعغفقكلمنهويءآأؤإئة")
                
                tessApi = tessApiInstance
                isTesseractInitialized = true
                Log.e(TAG, "Tesseract initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize Tesseract")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Tesseract: ${e.message}", e)
        }
    }
    
    private fun copyTessDataIfNeeded(context: Context, dataPath: String) {
        val tessDataDir = File(dataPath, "tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }
        
        val trainedData = File(tessDataDir, "ara.traineddata")
        if (!trainedData.exists()) {
            try {
                context.assets.open("tessdata/ara.traineddata").use { input ->
                    FileOutputStream(trainedData).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.e(TAG, "Copied ara.traineddata to ${tessDataDir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying tessdata: ${e.message}", e)
            }
        }
    }
    
    private fun extractWithTesseract(pdfFile: File, onProgress: ((Int, Int) -> Unit)? = null): String {
        if (!isTesseractInitialized) {
            Log.e(TAG, "Tesseract not initialized")
            return ""
        }
        
        return try {
            val pageCount = getPageCount(pdfFile)
            val allText = StringBuilder()
            
            for (pageNum in 1..pageCount) {
                onProgress?.invoke(pageNum, pageCount)
                
                val bitmap = renderPdfPageToBitmap(pdfFile, pageNum)
                if (bitmap != null) {
                    tessApi?.setImage(bitmap)
                    val pageText = tessApi?.getUTF8Text() ?: ""
                    allText.append(pageText).append("\n")
                    bitmap.recycle()
                }
            }
            
            val result = allText.toString().trim()
            Log.e(TAG, "Extracted ${result.length} chars with Tesseract")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract extraction error: ${e.message}", e)
            ""
        }
    }
    
    private fun renderPdfPageToBitmap(pdfFile: File, pageNum: Int): Bitmap? {
        var fileDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        
        try {
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fileDescriptor)
            
            if (pageNum - 1 < renderer.pageCount) {
                page = renderer.openPage(pageNum - 1)
                
                val scale = 2f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF page: ${e.message}", e)
        } finally {
            try { page?.close() } catch (e: Exception) {}
            try { renderer?.close() } catch (e: Exception) {}
            try { fileDescriptor?.close() } catch (e: Exception) {}
        }
        
        return bitmap
    }
    
    suspend fun extractTextFromPdf(
        pdfFile: File,
        onProgress: ((Int, Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        
        var document: PDDocument? = null
        
        return@withContext try {
            Log.e(TAG, "=== EXTRACTING PDF ===")
            Log.e(TAG, "File: ${pdfFile.absolutePath}")
            Log.e(TAG, "File exists: ${pdfFile.exists()}")
            Log.e(TAG, "File size: ${pdfFile.length()} bytes")
            
            document = PDDocument.load(pdfFile)
            val pageCount = document.numberOfPages
            
            Log.e(TAG, "PDF loaded: $pageCount pages")
            onProgress?.invoke(0, pageCount)
            
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.lineSeparator = "\n"
            
            var text = stripper.getText(document)
            
            Log.e(TAG, "PDFBox extracted ${text.length} characters")
            
            if (text.trim().isEmpty()) {
                Log.e(TAG, "PDFBox empty, trying Tesseract...")
                text = extractWithTesseract(pdfFile, onProgress)
            }
            
            if (text.isNotEmpty()) {
                Log.e(TAG, "Final text: ${text.length} chars")
                onProgress?.invoke(pageCount, pageCount)
            }
            
            text.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF: ${e.message}", e)
            
            try {
                Log.e(TAG, "Trying Tesseract as fallback...")
                extractWithTesseract(pdfFile, onProgress)
            } catch (e2: Exception) {
                Log.e(TAG, "Tesseract also failed: ${e2.message}", e2)
                ""
            }
        } finally {
            try { document?.close() } catch (e: Exception) {}
        }
    }
    
    suspend fun extractTextFromBase64Pdf(
        base64Pdf: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val pdfBytes = Base64.decode(base64Pdf, Base64.DEFAULT)
            val tempFile = File.createTempFile("pdf_", ".pdf")
            
            FileOutputStream(tempFile).use { it.write(pdfBytes) }
            
            val text = extractTextFromPdf(tempFile, onProgress)
            tempFile.delete()
            
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from base64: ${e.message}", e)
            ""
        }
    }
    
    suspend fun extractTextFromPdfByPage(
        pdfFile: File,
        onPageExtracted: ((Int, String) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        
        val pagesText = mutableListOf<String>()
        var document: PDDocument? = null
        
        return@withContext try {
            document = PDDocument.load(pdfFile)
            val pageCount = document.numberOfPages
            
            Log.d(TAG, "Extracting text by page from $pageCount pages")
            
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            
            for (pageIndex in 0 until pageCount) {
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                
                val pageText = stripper.getText(document).trim()
                pagesText.add(pageText)
                
                onPageExtracted?.invoke(pageIndex, pageText)
            }
            
            pagesText
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF by page: ${e.message}", e)
            emptyList()
        } finally {
            try { document?.close() } catch (e: Exception) {}
        }
    }
    
    fun getPageCount(pdfFile: File): Int {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(pdfFile)
            document.numberOfPages
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page count: ${e.message}", e)
            0
        } finally {
            try { document?.close() } catch (e: Exception) {}
        }
    }
    
    fun isValidPdf(pdfFile: File): Boolean {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(pdfFile)
            true
        } catch (e: Exception) {
            false
        } finally {
            try { document?.close() } catch (e: Exception) {}
        }
    }
}