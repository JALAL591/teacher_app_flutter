package com.edu.student.utils

import android.util.Base64
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfTextExtractor {
    
    private const val TAG = "PdfTextExtractor"
    
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
            
            val text = stripper.getText(document)
            
            Log.e(TAG, "Extracted ${text.length} characters from PDF")
            onProgress?.invoke(pageCount, pageCount)
            
            text.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF: ${e.message}", e)
            ""
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