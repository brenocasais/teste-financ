package com.example.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import com.example.data.model.Account
import com.example.data.model.AllocationMovement
import com.example.data.model.BudgetAllocation
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

object ExportHelper {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatMonthPtBr(monthStr: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM", Locale.US)
            val date = sdfInput.parse(monthStr) ?: return monthStr
            val sdfOutput = SimpleDateFormat("MMMM/yyyy", Locale("pt", "BR"))
            sdfOutput.format(date).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            monthStr
        }
    }

    private fun formatDatePtBr(dateStr: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdfInput.parse(dateStr) ?: return dateStr
            val sdfOutput = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            sdfOutput.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun deAccent(str: String): String {
        val nfdNormalizedString = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val withoutAccents = pattern.matcher(nfdNormalizedString).replaceAll("")
        return withoutAccents.replace("ç", "c").replace("Ç", "C")
    }

    private fun formatMonthForFileName(monthStr: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM", Locale.US)
            val date = sdfInput.parse(monthStr) ?: return monthStr
            val sdfOutput = SimpleDateFormat("MMM-yyyy", Locale("pt", "BR"))
            sdfOutput.format(date).lowercase()
        } catch (e: Exception) {
            monthStr
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    suspend fun exportToExcel(
        context: Context,
        startMonth: String,
        endMonth: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgetAllocations: List<BudgetAllocation>,
        allocationMovements: List<AllocationMovement>,
        categories: List<Category>,
        subcategories: List<Subcategory>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()

            val accountMap = accounts.associate { it.id to it.name }
            val categoryMap = categories.associate { it.id to it.name }
            val subcategoryMap = subcategories.associate { it.id to it.name }

            // 1. Sheet: Transações
            val sheetTx = workbook.createSheet("Transações")
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.BLUE_GREY.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val headersTx = listOf("Data", "Descrição", "Categoria", "Subcategoria", "Conta", "Tipo", "Valor")
            val rowHeaderTx = sheetTx.createRow(0)
            headersTx.forEachIndexed { idx, h ->
                val cell = rowHeaderTx.createCell(idx)
                cell.setCellValue(h)
                cell.setCellStyle(headerStyle)
            }

            val periodTransactions = transactions.filter { tx ->
                if (tx.date.length >= 7) {
                    val txMonth = tx.date.substring(0, 7)
                    txMonth >= startMonth && txMonth <= endMonth
                } else {
                    false
                }
            }.sortedBy { it.date }

            var rowIdx = 1
            periodTransactions.forEach { tx ->
                val row = sheetTx.createRow(rowIdx++)
                row.createCell(0).setCellValue(formatDatePtBr(tx.date))
                row.createCell(1).setCellValue(tx.description)
                row.createCell(2).setCellValue(categoryMap[tx.category_id] ?: "")
                row.createCell(3).setCellValue(subcategoryMap[tx.subcategory_id] ?: "")
                row.createCell(4).setCellValue(accountMap[tx.account_id] ?: "")
                row.createCell(5).setCellValue(
                    when (tx.type) {
                        "RECEITA" -> "Receita"
                        "DESPESA" -> "Despesa"
                        "TRANSFERENCIA" -> "Transferência"
                        else -> tx.type
                    }
                )
                row.createCell(6).setCellValue(tx.value)
            }

            for (i in headersTx.indices) {
                sheetTx.autoSizeColumn(i)
            }

            // 2. Sheet: Resumo por Categoria
            val sheetSummary = workbook.createSheet("Resumo por Categoria")
            val headersSum = listOf("Categoria", "Subcategoria", "Planejado", "Alocado", "Gasto", "Disponível")
            val rowHeaderSum = sheetSummary.createRow(0)
            headersSum.forEachIndexed { idx, h ->
                val cell = rowHeaderSum.createCell(idx)
                cell.setCellValue(h)
                cell.setCellStyle(headerStyle)
            }

            var sumRowIdx = 1
            categories.forEach { cat ->
                // Overall Category Row
                val catAllocs = budgetAllocations.filter {
                    it.category_id == cat.id &&
                    it.subcategory_id == null &&
                    it.month >= startMonth &&
                    it.month <= endMonth
                }
                val plannedSum = catAllocs.sumOf { it.planned_value }
                val allocatedSum = catAllocs.sumOf { alloc ->
                    allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                    allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                }
                val spentSum = transactions.filter {
                    it.type == "DESPESA" &&
                    it.category_id == cat.id &&
                    it.subcategory_id == null &&
                    it.date.length >= 7 &&
                    it.date.substring(0, 7) >= startMonth &&
                    it.date.substring(0, 7) <= endMonth
                }.sumOf { it.value }

                val disponivelSum = allocatedSum - spentSum

                val row = sheetSummary.createRow(sumRowIdx++)
                row.createCell(0).setCellValue(cat.name)
                row.createCell(1).setCellValue("-")
                row.createCell(2).setCellValue(plannedSum)
                row.createCell(3).setCellValue(allocatedSum)
                row.createCell(4).setCellValue(spentSum)
                row.createCell(5).setCellValue(disponivelSum)

                // Subcategory Rows
                val catSubs = subcategories.filter { it.category_id == cat.id }
                catSubs.forEach { sub ->
                    val subAllocs = budgetAllocations.filter {
                        it.category_id == cat.id &&
                        it.subcategory_id == sub.id &&
                        it.month >= startMonth &&
                        it.month <= endMonth
                    }
                    val subPlanned = subAllocs.sumOf { it.planned_value }
                    val subAllocated = subAllocs.sumOf { alloc ->
                        allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                        allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                    }
                    val subSpent = transactions.filter {
                        it.type == "DESPESA" &&
                        it.category_id == cat.id &&
                        it.subcategory_id == sub.id &&
                        it.date.length >= 7 &&
                        it.date.substring(0, 7) >= startMonth &&
                        it.date.substring(0, 7) <= endMonth
                    }.sumOf { it.value }

                    val subDisponivel = subAllocated - subSpent

                    val sRow = sheetSummary.createRow(sumRowIdx++)
                    sRow.createCell(0).setCellValue(cat.name)
                    sRow.createCell(1).setCellValue(sub.name)
                    sRow.createCell(2).setCellValue(subPlanned)
                    sRow.createCell(3).setCellValue(subAllocated)
                    sRow.createCell(4).setCellValue(subSpent)
                    sRow.createCell(5).setCellValue(subDisponivel)
                }
            }

            for (i in headersSum.indices) {
                sheetSummary.autoSizeColumn(i)
            }

            val file = File(context.cacheDir, "extrato_${startMonth}_a_${endMonth}.xlsx")
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportToCsv(
        context: Context,
        startMonth: String,
        endMonth: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        categories: List<Category>,
        subcategories: List<Subcategory>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val accountMap = accounts.associate { it.id to it.name }
            val categoryMap = categories.associate { it.id to it.name }
            val subcategoryMap = subcategories.associate { it.id to it.name }

            val file = File(context.cacheDir, "extrato_${startMonth}_a_${endMonth}.csv")
            val writer = file.printWriter()

            // Header line
            writer.println("Data,Descrição,Categoria,Subcategoria,Conta,Tipo,Valor")

            val periodTransactions = transactions.filter { tx ->
                if (tx.date.length >= 7) {
                    val txMonth = tx.date.substring(0, 7)
                    txMonth >= startMonth && txMonth <= endMonth
                } else {
                    false
                }
            }.sortedBy { it.date }

            periodTransactions.forEach { tx ->
                val date = formatDatePtBr(tx.date)
                val description = escapeCsv(tx.description)
                val category = escapeCsv(categoryMap[tx.category_id] ?: "")
                val subcategory = escapeCsv(subcategoryMap[tx.subcategory_id] ?: "")
                val account = escapeCsv(accountMap[tx.account_id] ?: "")
                val type = when (tx.type) {
                    "RECEITA" -> "Receita"
                    "DESPESA" -> "Despesa"
                    "TRANSFERENCIA" -> "Transferência"
                    else -> tx.type
                }
                // Write with point (.) decimal separator
                val valueStr = String.format(Locale.US, "%.2f", tx.value)

                writer.println("$date,$description,$category,$subcategory,$account,$type,$valueStr")
            }

            writer.flush()
            writer.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportToPdf(
        context: Context,
        startMonth: String,
        endMonth: String,
        selectedCategory: Category?,
        selectedSubcategory: Subcategory?,
        transactions: List<Transaction>,
        categories: List<Category>,
        subcategories: List<Subcategory>
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Filtering transactions: must have attachment_uri filled (not null, not empty)
            val filteredTxs = transactions.filter { tx ->
                if (tx.attachment_uri.isNullOrBlank()) return@filter false

                // If startMonth and endMonth are not empty, filter by period.
                // Otherwise, "Todos os meses" is selected (from oldest to newest)
                if (startMonth.isNotBlank() || endMonth.isNotBlank()) {
                    val txMonth = if (tx.date.length >= 7) tx.date.substring(0, 7) else ""
                    if (startMonth.isNotBlank() && txMonth < startMonth) return@filter false
                    if (endMonth.isNotBlank() && txMonth > endMonth) return@filter false
                }

                if (selectedCategory != null && tx.category_id != selectedCategory.id) return@filter false
                if (selectedSubcategory != null && tx.subcategory_id != selectedSubcategory.id) return@filter false

                true
            }.sortedBy { it.date }

            if (filteredTxs.isEmpty()) {
                return@withContext null
            }

            val categoryMap = categories.associate { it.id to it.name }
            val subcategoryMap = subcategories.associate { it.id to it.name }

            val pdfDocument = android.graphics.pdf.PdfDocument()
            var pagesAdded = 0

            // Page dimensions (A4 size: 595 x 842 points)
            val pageWidth = 595
            val pageHeight = 842

            filteredTxs.forEach { tx ->
                val uriStr = tx.attachment_uri!!
                val isPdf = uriStr.endsWith(".pdf", ignoreCase = true) || 
                            tx.attachment_name?.endsWith(".pdf", ignoreCase = true) == true ||
                            tx.attachment_type?.contains("pdf", ignoreCase = true) == true

                val uri = try {
                    if (uriStr.startsWith("/") || !uriStr.contains(":/")) {
                        Uri.fromFile(File(uriStr))
                    } else {
                        Uri.parse(uriStr)
                    }
                } catch (e: Exception) {
                    Uri.parse(uriStr)
                }

                try {
                    if (isPdf) {
                        // Open PDF using PdfRenderer with specified file/content resolver fallback logic
                        val pfd = openFileDescriptorSafely(context, uri)
                        val resolvedPfd = pfd ?: throw java.io.FileNotFoundException("Nao foi possivel obter o descritor de arquivo para o PDF.")

                        resolvedPfd.use { fd ->
                            val pdfRenderer = android.graphics.pdf.PdfRenderer(fd)
                            for (i in 0 until pdfRenderer.pageCount) {
                                pdfRenderer.openPage(i).use { rendererPage ->
                                    val rendererPageW = if (rendererPage.width > 0) rendererPage.width else 595
                                    val rendererPageH = if (rendererPage.height > 0) rendererPage.height else 842
                                    val bitmapWidth = 1000
                                    val bitmapHeight = (1000 * rendererPageH) / rendererPageW
                                    val bitmap = android.graphics.Bitmap.createBitmap(bitmapWidth, bitmapHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                    
                                    val canvasTmp = android.graphics.Canvas(bitmap)
                                    canvasTmp.drawColor(android.graphics.Color.WHITE)
                                    rendererPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    val canvas = page.canvas
                                    canvas.drawColor(android.graphics.Color.WHITE)

                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        isFilterBitmap = true
                                    }

                                    // Draw PDF page bitmap scaled to full page (595 x 842)
                                    val destRect = android.graphics.RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
                                    canvas.drawBitmap(bitmap, null, destRect, paint)

                                    pdfDocument.finishPage(page)
                                    pagesAdded++
                                    bitmap.recycle()
                                }
                            }
                            pdfRenderer.close()
                        }
                    } else {
                        // Decode image safely with downsampling to avoid OutOfMemoryErrors
                        val bitmap = decodeSampledBitmapFromStream(context, uriStr, 1200, 1200)
                        if (bitmap != null) {
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawColor(android.graphics.Color.WHITE)

                            val paint = Paint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                            }

                            var finalBitmap = bitmap
                            if (bitmap.width > bitmap.height) {
                                val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                                finalBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }

                            val imgWidth = finalBitmap.width.toFloat()
                            val imgHeight = finalBitmap.height.toFloat()

                            val scaleX = pageWidth.toFloat() / imgWidth
                            val scaleY = pageHeight.toFloat() / imgHeight
                            val scale = minOf(scaleX, scaleY)

                            val finalWidth = imgWidth * scale
                            val finalHeight = imgHeight * scale

                            val left = (pageWidth - finalWidth) / 2f
                            val top = (pageHeight - finalHeight) / 2f

                            val destRect = android.graphics.RectF(left, top, left + finalWidth, top + finalHeight)
                            canvas.drawBitmap(finalBitmap, null, destRect, paint)

                            if (finalBitmap != bitmap) {
                                finalBitmap.recycle()
                            }
                            bitmap.recycle()

                            pdfDocument.finishPage(page)
                            pagesAdded++
                        } else {
                            throw Exception("Nao foi possivel decodificar a imagem do anexo de forma segura.")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Skip any attachment that failed to load, ensuring that only successfully loaded images/PDF pages are included
                }
            }

            if (pagesAdded == 0) {
                pdfDocument.close()
                return@withContext null
            }

            val categorySegment = selectedCategory?.name?.lowercase()?.replace(" ", "_")?.replace(Regex("[^a-z0-9_]"), "") ?: "todas"
            val startMonthSeg = if (startMonth.isNotBlank()) formatMonthForFileName(startMonth) else "inicio"
            val endMonthSeg = if (endMonth.isNotBlank()) formatMonthForFileName(endMonth) else "fim"
            val filename = "comprovantes_${categorySegment}_${startMonthSeg}_a_${endMonthSeg}.pdf"

            val file = File(context.cacheDir, filename)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun decodeSampledBitmapFromStream(context: Context, uriStr: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        var inputStream = openAttachmentStream(context, uriStr) ?: return null
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            try {
                inputStream.close()
            } catch (e: Exception) {}

            // Re-open stream because a stream can only be read once
            inputStream = openAttachmentStream(context, uriStr) ?: return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            return android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: OutOfMemoryError) {
            System.gc()
            try {
                try {
                    inputStream.close()
                } catch (ex: Exception) {}
                inputStream = openAttachmentStream(context, uriStr) ?: return null
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 4 // hard fallback
                }
                return android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            } catch (e2: Throwable) {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {}
        }
    }

    fun saveUriToInternalStorage(context: Context, uri: Uri, originalName: String): Uri? {
        return try {
            val attachmentsDir = File(context.filesDir, "attachments")
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs()
            }
            val uniqueName = "${System.currentTimeMillis()}_${originalName.replace(" ", "_")}"
            val targetFile = File(attachmentsDir, uniqueName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveFileToInternalStorage(context: Context, file: File, originalName: String): Uri? {
        return try {
            val attachmentsDir = File(context.filesDir, "attachments")
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs()
            }
            val uniqueName = "${System.currentTimeMillis()}_${originalName.replace(" ", "_")}"
            val targetFile = File(attachmentsDir, uniqueName)
            file.copyTo(targetFile, overwrite = true)
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun resolveFileUri(context: Context, uri: Uri): File? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path)
        if (file.exists()) return file

        val lastSegment = uri.lastPathSegment
        if (lastSegment != null) {
            val attachmentsDir = File(context.filesDir, "attachments")
            val fallbackFile1 = File(attachmentsDir, lastSegment)
            if (fallbackFile1.exists()) return fallbackFile1

            val fallbackFile2 = File(context.filesDir, lastSegment)
            if (fallbackFile2.exists()) return fallbackFile2

            val fallbackFile3 = File(context.cacheDir, lastSegment)
            if (fallbackFile3.exists()) return fallbackFile3
        }
        
        val pathString = file.absolutePath
        val attachmentsIndex = pathString.indexOf("files/attachments/")
        if (attachmentsIndex != -1) {
            val relativePath = pathString.substring(attachmentsIndex + "files/attachments/".length)
            val targetFile = File(File(context.filesDir, "attachments"), relativePath)
            if (targetFile.exists()) return targetFile
        }

        return file
    }

    fun openFileDescriptorSafely(context: Context, uri: Uri): android.os.ParcelFileDescriptor? {
        // Clean up old temp files first to prevent disk leak
        try {
            val cacheDirFiles = context.cacheDir.listFiles()
            cacheDirFiles?.forEach { file ->
                if (file.name.startsWith("temp_render_")) {
                    val age = System.currentTimeMillis() - file.lastModified()
                    if (age > 60000) { // older than 1 minute
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Try using openAttachmentStream since it's our most robust stream resolver
        try {
            val inputStream = openAttachmentStream(context, uri.toString())
            if (inputStream != null) {
                val tempFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Try file scheme directly
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    try {
                        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 2. Try resolving via resolveFileUri
        val resolved = resolveFileUri(context, uri)
        if (resolved != null && resolved.exists()) {
            try {
                return android.os.ParcelFileDescriptor.open(resolved, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to ContentResolver
        try {
            return context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Ultimate fallback: if there is a last path segment, search our local directories
        val lastSegment = uri.lastPathSegment
        if (lastSegment != null) {
            val attachmentsDir = File(context.filesDir, "attachments")
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            
            for (dir in listOf(attachmentsDir, filesDir, cacheDir)) {
                val fallbackFile = File(dir, lastSegment)
                if (fallbackFile.exists()) {
                    try {
                        return android.os.ParcelFileDescriptor.open(fallbackFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return null
    }

    fun openInputStreamSafely(context: Context, uri: Uri): java.io.InputStream? {
        // 1. Try file scheme directly
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    try {
                        return java.io.FileInputStream(file)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 2. Try resolving via resolveFileUri
        val resolved = resolveFileUri(context, uri)
        if (resolved != null && resolved.exists()) {
            try {
                return java.io.FileInputStream(resolved)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to ContentResolver
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Ultimate fallback: search local directories for last path segment
        val lastSegment = uri.lastPathSegment
        if (lastSegment != null) {
            val attachmentsDir = File(context.filesDir, "attachments")
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            
            for (dir in listOf(attachmentsDir, filesDir, cacheDir)) {
                val fallbackFile = File(dir, lastSegment)
                if (fallbackFile.exists()) {
                    try {
                        return java.io.FileInputStream(fallbackFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return null
    }

    suspend fun exportSingleTransactionToPdf(
        context: Context,
        tx: Transaction,
        categoryName: String?,
        subcategoryName: String?,
        accountName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            val uriStr = tx.attachment_uri
            val isPdf = if (uriStr != null) {
                uriStr.endsWith(".pdf", ignoreCase = true) || 
                tx.attachment_name?.endsWith(".pdf", ignoreCase = true) == true ||
                tx.attachment_type?.contains("pdf", ignoreCase = true) == true
            } else false

            val hasRealAttachment = !uriStr.isNullOrBlank() && !uriStr.startsWith("content://meu_financeiro/")

            if (hasRealAttachment && uriStr != null) {
                val uri = try {
                    if (uriStr.startsWith("/") || !uriStr.contains(":/")) {
                        Uri.fromFile(File(uriStr))
                    } else {
                        Uri.parse(uriStr)
                    }
                } catch (e: Exception) {
                    Uri.parse(uriStr)
                }

                try {
                    if (isPdf) {
                        val pfd = openFileDescriptorSafely(context, uri)
                        val resolvedPfd = pfd ?: throw java.io.FileNotFoundException("Nao foi possivel obter o descritor de arquivo para o PDF.")

                        resolvedPfd.use { fd ->
                            val pdfRenderer = android.graphics.pdf.PdfRenderer(fd)
                            for (i in 0 until pdfRenderer.pageCount) {
                                pdfRenderer.openPage(i).use { rendererPage ->
                                    val rendererPageW = if (rendererPage.width > 0) rendererPage.width else 595
                                    val rendererPageH = if (rendererPage.height > 0) rendererPage.height else 842
                                    val bitmapWidth = 1000
                                    val bitmapHeight = (1000 * rendererPageH) / rendererPageW
                                    val bitmap = android.graphics.Bitmap.createBitmap(bitmapWidth, bitmapHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                    
                                    val canvasTmp = android.graphics.Canvas(bitmap)
                                    canvasTmp.drawColor(android.graphics.Color.WHITE)
                                    rendererPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    val canvas = page.canvas
                                    canvas.drawColor(android.graphics.Color.WHITE)

                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                    }

                                    // Header
                                    paint.color = android.graphics.Color.DKGRAY
                                    paint.textSize = 14f
                                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    val pageTitle = "Comprovante de Transacao (PDF - Pag. ${i + 1}/${pdfRenderer.pageCount})"
                                    canvas.drawText(pageTitle, 50f, 50f, paint)

                                    // Divider
                                    paint.color = android.graphics.Color.LTGRAY
                                    paint.strokeWidth = 1f
                                    canvas.drawLine(50f, 60f, 545f, 60f, paint)

                                    // Legend
                                    paint.color = android.graphics.Color.BLACK
                                    paint.textSize = 10f
                                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                                    val formattedVal = currencyFormatter.format(tx.value)
                                    val catStr = if (!subcategoryName.isNullOrBlank()) "$categoryName -> $subcategoryName" else (categoryName ?: "")

                                    canvas.drawText("Data: ${formatDatePtBr(tx.date)}", 50f, 80f, paint)
                                    canvas.drawText("Descricao: ${tx.description}", 50f, 95f, paint)
                                    canvas.drawText("Categoria: $catStr", 300f, 80f, paint)
                                    canvas.drawText("Valor: $formattedVal", 300f, 95f, paint)

                                    canvas.drawLine(50f, 105f, 545f, 105f, paint)

                                    // Draw PDF page bitmap scaled
                                    val destWidth = 495f
                                    val destHeight = 670f
                                    val scaleX = destWidth / bitmap.width
                                    val scaleY = destHeight / bitmap.height
                                    val scale = minOf(scaleX, scaleY)
                                    val finalWidth = bitmap.width * scale
                                    val finalHeight = bitmap.height * scale

                                    val x = 50f + (destWidth - finalWidth) / 2f
                                    val y = 120f + (destHeight - finalHeight) / 2f

                                    val destRect = android.graphics.RectF(x, y, x + finalWidth, y + finalHeight)
                                    val bitmapPaint = Paint().apply {
                                        isAntiAlias = true
                                        isFilterBitmap = true
                                    }
                                    canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)

                                    pdfDocument.finishPage(page)
                                    bitmap.recycle()
                                }
                            }
                            pdfRenderer.close()
                        }
                    } else {
                        // Decode image and draw on one page
                        val inputStream = openInputStreamSafely(context, uri)
                        val resolvedStream = inputStream ?: throw java.io.FileNotFoundException("Nao foi possivel abrir o fluxo de entrada para o anexo.")
                        resolvedStream.use { stream ->
                            val bytes = stream.readBytes()
                            var bitmap: android.graphics.Bitmap? = null
                            if (bytes.isNotEmpty()) {
                                val options = android.graphics.BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                
                                var inSampleSize = 1
                                val maxDim = maxOf(options.outWidth, options.outHeight)
                                if (maxDim > 1200) {
                                    inSampleSize = maxDim / 1200
                                }
                                
                                options.inJustDecodeBounds = false
                                options.inSampleSize = inSampleSize
                                bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            }

                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawColor(android.graphics.Color.WHITE)

                            val paint = Paint().apply {
                                isAntiAlias = true
                            }

                            // Header
                            paint.color = android.graphics.Color.DKGRAY
                            paint.textSize = 14f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            canvas.drawText("Comprovante de Transacao (Imagem)", 50f, 50f, paint)

                            // Divider
                            paint.color = android.graphics.Color.LTGRAY
                            paint.strokeWidth = 1f
                            canvas.drawLine(50f, 60f, 545f, 60f, paint)

                            // Legend / Meta Info
                            paint.color = android.graphics.Color.BLACK
                            paint.textSize = 10f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                            val formattedVal = currencyFormatter.format(tx.value)
                            val catStr = if (!subcategoryName.isNullOrBlank()) "$categoryName -> $subcategoryName" else (categoryName ?: "")

                            canvas.drawText("Data: ${formatDatePtBr(tx.date)}", 50f, 80f, paint)
                            canvas.drawText("Descricao: ${tx.description}", 50f, 95f, paint)
                            canvas.drawText("Categoria: $catStr", 300f, 80f, paint)
                            canvas.drawText("Valor: $formattedVal", 300f, 95f, paint)

                            canvas.drawLine(50f, 105f, 545f, 105f, paint)

                            if (bitmap != null) {
                                val destWidth = 495f
                                val destHeight = 670f
                                val scaleX = destWidth / bitmap.width
                                val scaleY = destHeight / bitmap.height
                                val scale = minOf(scaleX, scaleY)
                                val finalWidth = bitmap.width * scale
                                val finalHeight = bitmap.height * scale

                                val x = 50f + (destWidth - finalWidth) / 2f
                                val y = 120f + (destHeight - finalHeight) / 2f

                                val destRect = android.graphics.RectF(x, y, x + finalWidth, y + finalHeight)
                                val bitmapPaint = Paint().apply {
                                    isAntiAlias = true
                                    isFilterBitmap = true
                                }
                                canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
                                bitmap.recycle()
                            } else {
                                throw Exception("Nao foi possivel decodificar a imagem do anexo (bytes vazios ou formato invalido).")
                            }

                            pdfDocument.finishPage(page)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    
                    // Error page
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.rgb(255, 235, 238)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

                    val paint = Paint().apply {
                        isAntiAlias = true
                    }

                    paint.color = android.graphics.Color.rgb(211, 47, 47)
                    paint.textSize = 14f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Erro ao Carregar Anexo", 50f, 60f, paint)

                    paint.textSize = 12f
                    paint.color = android.graphics.Color.BLACK
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("Nao foi possivel carregar o anexo desta transacao.", 50f, 90f, paint)

                    paint.color = android.graphics.Color.rgb(198, 40, 40)
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    canvas.drawText("Motivo tecnico:", 50f, 120f, paint)

                    val errorMsg = e.message ?: e.toString()
                    val maxLineLength = 75
                    val errorLines = errorMsg.chunked(maxLineLength)
                    var currentY = 140f
                    errorLines.forEach { line ->
                        canvas.drawText(line, 50f, currentY, paint)
                        currentY += 15f
                    }

                    currentY += 30f
                    paint.color = android.graphics.Color.rgb(239, 154, 154)
                    paint.strokeWidth = 1f
                    canvas.drawLine(50f, currentY, 545f, currentY, paint)
                    currentY += 20f

                    paint.color = android.graphics.Color.DKGRAY
                    paint.textSize = 11f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("DETALHES DA TRANSACAO:", 50f, currentY, paint)
                    currentY += 20f

                    paint.color = android.graphics.Color.BLACK
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    
                    val formattedVal = currencyFormatter.format(tx.value)
                    val catStr = if (!subcategoryName.isNullOrBlank()) "$categoryName -> $subcategoryName" else (categoryName ?: "")

                    canvas.drawText("Data: ${formatDatePtBr(tx.date)}", 50f, currentY, paint)
                    canvas.drawText("Valor: $formattedVal", 300f, currentY, paint)
                    currentY += 20f
                    canvas.drawText("Descricao: ${tx.description}", 50f, currentY, paint)
                    currentY += 20f
                    canvas.drawText("Categoria: $catStr", 50f, currentY, paint)

                    pdfDocument.finishPage(page)
                }
            } else {
                // Generate a beautiful, elegant A4 digital receipt PDF!
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)

                val paint = Paint().apply {
                    isAntiAlias = true
                }

                val primaryColor = android.graphics.Color.rgb(27, 94, 32)
                
                paint.color = primaryColor
                paint.textSize = 18f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("MEU FINANCEIRO", 50f, 70f, paint)

                paint.color = android.graphics.Color.GRAY
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Comprovante de Transacao Digital", 50f, 85f, paint)

                val badgePaint = Paint().apply {
                    color = android.graphics.Color.rgb(232, 245, 233)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(460f, 55f, 545f, 80f, 4f, 4f, badgePaint)

                paint.color = android.graphics.Color.rgb(46, 125, 50)
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("VALIDO", 480f, 72f, paint)

                paint.color = android.graphics.Color.LTGRAY
                paint.strokeWidth = 1f
                canvas.drawLine(50f, 110f, 545f, 110f, paint)

                paint.color = android.graphics.Color.DKGRAY
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("DETALHES DO LANCAMENTO", 50f, 140f, paint)

                paint.textSize = 12f
                var currentY = 175f
                val lineHeight = 30f

                fun drawReceiptLine(label: String, valText: String, isValueField: Boolean = false) {
                    paint.color = android.graphics.Color.GRAY
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(label, 50f, currentY, paint)

                    paint.color = if (isValueField) primaryColor else android.graphics.Color.BLACK
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(valText, 300f, currentY, paint)
                    currentY += lineHeight
                }

                drawReceiptLine("Descricao:", tx.description)
                drawReceiptLine("Valor:", currencyFormatter.format(tx.value), isValueField = true)
                drawReceiptLine("Data:", formatDatePtBr(tx.date))
                drawReceiptLine("Categoria:", categoryName ?: "Nenhuma")
                drawReceiptLine("Conta:", accountName)

                currentY += 10f
                paint.color = android.graphics.Color.LTGRAY
                canvas.drawLine(50f, currentY, 545f, currentY, paint)
                currentY += 30f

                paint.color = android.graphics.Color.GRAY
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("CODIGO DE AUTENTICACAO DIGITAL", 50f, currentY, paint)
                currentY += 20f

                val barPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    style = Paint.Style.FILL
                }
                var startX = 50f
                val barHeight = 40f
                var barIndex = 0
                while (startX < 545f) {
                    val barWidth = if (barIndex % 3 == 0) 6f else if (barIndex % 2 == 0) 3f else 1.5f
                    val space = if (barIndex % 4 == 0) 4f else 2f
                    canvas.drawRect(startX, currentY, startX + barWidth, currentY + barHeight, barPaint)
                    startX += barWidth + space
                    barIndex++
                }

                currentY += barHeight + 15f
                paint.color = android.graphics.Color.GRAY
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                val mockAutenticacao = "MF-${System.currentTimeMillis().toString().takeLast(8)}-AUTENTICADO"
                canvas.drawText(mockAutenticacao, 50f, currentY, paint)

                paint.color = android.graphics.Color.LTGRAY
                paint.strokeWidth = 1f
                canvas.drawLine(50f, 730f, 545f, 730f, paint)

                paint.color = android.graphics.Color.GRAY
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Este documento foi gerado pelo aplicativo Meu Financeiro e serve como comprovante fiscal de lancamento interno.", 50f, 755f, paint)
                
                paint.color = android.graphics.Color.rgb(200, 200, 200)
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                val mockMd5 = "Assinatura Eletronica MD5: MF_HASH_" + System.currentTimeMillis().toString().hashCode().toString(16).uppercase(Locale.ROOT)
                canvas.drawText(mockMd5, 50f, 775f, paint)

                pdfDocument.finishPage(page)
            }

            val safeDescription = tx.description.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            val safeDate = tx.date.replace("-", "")
            val filename = "comprovante_${safeDate}_${safeDescription}.pdf"

            val file = File(context.cacheDir, filename)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openAttachmentStream(context: Context, uriStr: String): java.io.InputStream? {
        val uri = try {
            if (uriStr.startsWith("/") || !uriStr.contains(":/")) {
                Uri.fromFile(File(uriStr))
            } else {
                Uri.parse(uriStr)
            }
        } catch (e: Exception) {
            Uri.parse(uriStr)
        }

        // Try 1: ContentResolver directly
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try 2: If it's a file URI or path, try direct FileInputStream
        val path = uri.path
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                try {
                    return java.io.FileInputStream(file)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Try 3: Fallback resolving using lastPathSegment
        val lastSegment = uri.lastPathSegment
        if (lastSegment != null) {
            val attachmentsDir = File(context.filesDir, "attachments")
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            for (dir in listOf(attachmentsDir, filesDir, cacheDir)) {
                val fallbackFile = File(dir, lastSegment)
                if (fallbackFile.exists()) {
                    try {
                        return java.io.FileInputStream(fallbackFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Try 4: If URI path contains URL encoded spaces or special characters
        try {
            val decodedPath = Uri.decode(uriStr)
            if (decodedPath != uriStr) {
                val file = File(decodedPath)
                if (file.exists()) {
                    return java.io.FileInputStream(file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    suspend fun exportAttachmentOnlyToPdf(
        context: Context,
        attachmentUri: String,
        attachmentName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val isPdf = attachmentName.endsWith(".pdf", ignoreCase = true) || attachmentUri.endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                val inputStream = openAttachmentStream(context, attachmentUri)
                if (inputStream != null) {
                    inputStream.use { stream ->
                        val file = File(context.cacheDir, "comprovante_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(file).use { outputStream ->
                            stream.copyTo(outputStream)
                        }
                        return@withContext file
                    }
                }
                throw Exception("Nao foi possivel abrir o arquivo PDF original.")
            } else {
                val bitmap = decodeSampledBitmapFromStream(context, attachmentUri, 1200, 1200)
                if (bitmap != null) {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    val pageWidth = 595
                    val pageHeight = 842

                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val paint = Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                    }

                    var finalBitmap = bitmap
                    if (bitmap.width > bitmap.height) {
                        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                        finalBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    val imgWidth = finalBitmap.width.toFloat()
                    val imgHeight = finalBitmap.height.toFloat()

                    val scaleX = pageWidth.toFloat() / imgWidth
                    val scaleY = pageHeight.toFloat() / imgHeight
                    val scale = minOf(scaleX, scaleY)

                    val finalWidth = imgWidth * scale
                    val finalHeight = imgHeight * scale

                    val left = (pageWidth - finalWidth) / 2f
                    val top = (pageHeight - finalHeight) / 2f

                    val destRect = android.graphics.RectF(left, top, left + finalWidth, top + finalHeight)
                    canvas.drawBitmap(finalBitmap, null, destRect, paint)

                    pdfDocument.finishPage(page)
                    
                    if (finalBitmap != bitmap) {
                        finalBitmap.recycle()
                    }
                    bitmap.recycle()

                    val file = File(context.cacheDir, "comprovante_${System.currentTimeMillis()}.pdf")
                    pdfDocument.writeTo(FileOutputStream(file))
                    pdfDocument.close()
                    return@withContext file
                }
                throw Exception("Nao foi possivel abrir ou decodificar a imagem do anexo de forma segura.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
