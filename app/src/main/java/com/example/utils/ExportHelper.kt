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
            // Filtering transactions
            val filteredTxs = transactions.filter { tx ->
                if (tx.attachment_uri.isNullOrBlank()) return@filter false

                val txMonth = if (tx.date.length >= 7) tx.date.substring(0, 7) else ""
                val inPeriod = txMonth >= startMonth && txMonth <= endMonth
                if (!inPeriod) return@filter false

                if (selectedCategory != null && tx.category_id != selectedCategory.id) return@filter false
                if (selectedSubcategory != null && tx.subcategory_id != selectedSubcategory.id) return@filter false

                true
            }.sortedBy { it.date }

            if (filteredTxs.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Nenhum comprovante encontrado nesse período/categoria", Toast.LENGTH_LONG).show()
                }
                return@withContext null
            }

            val categoryMap = categories.associate { it.id to it.name }
            val subcategoryMap = subcategories.associate { it.id to it.name }

            val pdfDocument = android.graphics.pdf.PdfDocument()

            // Page dimensions (A4 size: 595 x 842 points)
            val pageWidth = 595
            val pageHeight = 842

            filteredTxs.forEach { tx ->
                val uriStr = tx.attachment_uri!!
                val isPdf = uriStr.endsWith(".pdf", ignoreCase = true) || 
                            tx.attachment_name?.endsWith(".pdf", ignoreCase = true) == true ||
                            tx.attachment_type?.contains("pdf", ignoreCase = true) == true

                try {
                    val uri = Uri.parse(uriStr)
                    if (isPdf) {
                        // Render PDF pages using native PdfRenderer
                        val pfd = if (uri.scheme == "file") {
                            val filePath = uri.path
                            if (filePath != null && File(filePath).exists()) {
                                android.os.ParcelFileDescriptor.open(File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            } else {
                                context.contentResolver.openFileDescriptor(uri, "r")
                            }
                        } else {
                            context.contentResolver.openFileDescriptor(uri, "r")
                        }
                        pfd?.use { fd ->
                            val pdfRenderer = android.graphics.pdf.PdfRenderer(fd)
                            for (i in 0 until pdfRenderer.pageCount) {
                                pdfRenderer.openPage(i).use { rendererPage ->
                                    // A4 aspect ratio bitmap representation of page
                                    val rendererPageW = if (rendererPage.width > 0) rendererPage.width else 595
                                    val rendererPageH = if (rendererPage.height > 0) rendererPage.height else 842
                                    val bitmapWidth = 1000
                                    val bitmapHeight = (1000 * rendererPageH) / rendererPageW
                                    val bitmap = android.graphics.Bitmap.createBitmap(bitmapWidth, bitmapHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                    
                                    // Paint page white first to have clean background
                                    val canvasTmp = android.graphics.Canvas(bitmap)
                                    canvasTmp.drawColor(android.graphics.Color.WHITE)
                                    
                                    rendererPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    // Add native Pdf page
                                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    val canvas = page.canvas
                                    canvas.drawColor(android.graphics.Color.WHITE)

                                    // Draw Header/Info Text
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                    }

                                    // Title
                                    paint.color = android.graphics.Color.DKGRAY
                                    paint.textSize = 14f
                                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    canvas.drawText("Comprovante de Transacao (PDF)", 50f, 50f, paint)

                                    // Meta Info
                                    paint.color = android.graphics.Color.GRAY
                                    paint.textSize = 10f
                                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                                    val formattedVal = currencyFormatter.format(tx.value)
                                    canvas.drawText("Data: ${formatDatePtBr(tx.date)}  |  Valor: $formattedVal", 50f, 75f, paint)
                                    canvas.drawText("Descricao: ${tx.description}", 50f, 90f, paint)

                                    val catName = categoryMap[tx.category_id] ?: ""
                                    val subcatName = subcategoryMap[tx.subcategory_id] ?: ""
                                    val catStr = "$catName ${if (subcatName.isNotBlank()) "-> $subcatName" else ""}"
                                    canvas.drawText("Categoria: $catStr", 50f, 105f, paint)

                                    // Scale and Draw rendered PDF page onto canvas
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
                        // Decode image and draw
                        val inputStream = try {
                            if (uri.scheme == "file") {
                                val filePath = uri.path
                                if (filePath != null && File(filePath).exists()) {
                                    java.io.FileInputStream(File(filePath))
                                } else {
                                    context.contentResolver.openInputStream(uri)
                                }
                            } else {
                                context.contentResolver.openInputStream(uri)
                            }
                        } catch (e: Exception) {
                            context.contentResolver.openInputStream(uri)
                        }
                        val stream = inputStream ?: throw java.io.FileNotFoundException("Nao foi possivel abrir o fluxo de entrada para o anexo.")
                        stream.use { stream ->
                            val bytes = stream.readBytes()
                            var bitmap: android.graphics.Bitmap? = null
                            if (bytes.isNotEmpty()) {
                                try {
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
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }

                            // Create the PDF page regardless of whether bitmap decoding succeeded
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawColor(android.graphics.Color.WHITE)

                            val paint = Paint().apply {
                                isAntiAlias = true
                            }

                            // Title
                            paint.color = android.graphics.Color.DKGRAY
                            paint.textSize = 14f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            canvas.drawText("Comprovante de Transacao", 50f, 50f, paint)

                            // Meta Info
                            paint.color = android.graphics.Color.GRAY
                            paint.textSize = 10f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            val formattedVal = currencyFormatter.format(tx.value)
                            canvas.drawText("Data: ${formatDatePtBr(tx.date)}  |  Valor: $formattedVal", 50f, 75f, paint)
                            canvas.drawText("Descricao: ${tx.description}", 50f, 90f, paint)

                            val catName = categoryMap[tx.category_id] ?: ""
                            val subcatName = subcategoryMap[tx.subcategory_id] ?: ""
                            val catStr = "$catName ${if (subcatName.isNotBlank()) "-> $subcatName" else ""}"
                            canvas.drawText("Categoria: $catStr", 50f, 105f, paint)

                            if (bitmap != null) {
                                // Scale and Draw Image
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
                                // Draw placeholder message when image is not renderable directly
                                paint.color = android.graphics.Color.RED
                                paint.textSize = 12f
                                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                                canvas.drawText("Anexo: ${tx.attachment_name ?: "Imagem"}", 50f, 140f, paint)
                                paint.color = android.graphics.Color.GRAY
                                canvas.drawText("(O arquivo de comprovante esta anexado a transacao no app)", 50f, 160f, paint)
                            }

                            pdfDocument.finishPage(page)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Create an error fallback page to prevent breaking the overall document
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = Paint().apply {
                        isAntiAlias = true
                    }
                    paint.color = android.graphics.Color.RED
                    paint.textSize = 12f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    
                    val uri = try { Uri.parse(uriStr) } catch (ex: Exception) { null }
                    val errorMsg = if (e is java.io.FileNotFoundException) {
                        val caminho = uri?.path ?: uriStr
                        "Arquivo nao encontrado no caminho: $caminho"
                    } else {
                        e.message ?: "Erro desconhecido ao carregar o anexo"
                    }
                    
                    canvas.drawText("Erro ao carregar o anexo:", 50f, 50f, paint)
                    
                    paint.color = android.graphics.Color.RED
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    
                    val maxLineLength = 80
                    val errorLines = errorMsg.chunked(maxLineLength)
                    var currentY = 70f
                    errorLines.forEach { line ->
                        canvas.drawText(line, 50f, currentY, paint)
                        currentY += 15f
                    }
                    
                    paint.color = android.graphics.Color.GRAY
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("Data: ${formatDatePtBr(tx.date)} | Descricao: ${tx.description}", 50f, currentY + 15f, paint)
                    pdfDocument.finishPage(page)
                }
            }

            val categorySegment = selectedCategory?.name?.lowercase()?.replace(" ", "_")?.replace(Regex("[^a-z0-9_]"), "") ?: "todas"
            val startMonthSeg = formatMonthForFileName(startMonth)
            val endMonthSeg = formatMonthForFileName(endMonth)
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
}
