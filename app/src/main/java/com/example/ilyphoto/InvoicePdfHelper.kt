package com.example.ilyphoto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.ilyphoto.config.BusinessConfig
import com.example.ilyphoto.data.Booking
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfHelper {

    fun generateAndSaveInvoicePdf(context: Context, booking: Booking): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 (595 x 842 points)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Header Banner (Lilac Accent #7E57C2)
        paint.color = Color.parseColor("#7E57C2")
        canvas.drawRect(0f, 0f, 595f, 110f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("ILY PHOTO STUDIO", 40f, 50f, paint)

        // Header Subtitle
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Capturing Every Moment • Professional Photography", 40f, 72f, paint)
        canvas.drawText("Strandfontein, Cape Town • ${BusinessConfig.CONTACT_PHONE}", 40f, 90f, paint)

        // "TAX INVOICE" label top right
        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TAX INVOICE", 555f, 55f, paint)

        val invNum = booking.invoiceNumber.ifBlank { "INV-" + booking.id.take(6).uppercase() }
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText(invNum, 555f, 75f, paint)

        val todayDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText(todayDate, 555f, 92f, paint)

        // Reset text alignment
        paint.textAlign = Paint.Align.LEFT

        // 2. Client Details Section
        var y = 145f
        paint.color = Color.parseColor("#2A2233")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("BILLED TO:", 40f, y, paint)

        y += 18f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val clientName = booking.clientName.ifBlank { "Valued Client" }
        canvas.drawText(clientName, 40f, y, paint)

        if (booking.clientEmail.isNotBlank()) {
            y += 15f
            canvas.drawText("Email: ${booking.clientEmail}", 40f, y, paint)
        }
        if (booking.clientPhone.isNotBlank()) {
            y += 15f
            canvas.drawText("Phone: ${booking.clientPhone}", 40f, y, paint)
        }
        y += 15f
        canvas.drawText("Event Location: ${booking.eventLocation.ifBlank { "Cape Town Area" }}", 40f, y, paint)

        // 3. Payment Status Badge Box (Right side)
        val status = booking.invoiceStatus.ifBlank { "Unpaid" }
        val statusBgColor = when (status.lowercase()) {
            "paid" -> Color.parseColor("#E8F5E9")
            "partially paid" -> Color.parseColor("#FFF3E0")
            else -> Color.parseColor("#FFEBEE")
        }
        val statusTextColor = when (status.lowercase()) {
            "paid" -> Color.parseColor("#2E7D32")
            "partially paid" -> Color.parseColor("#E65100")
            else -> Color.parseColor("#C62828")
        }

        paint.color = statusBgColor
        canvas.drawRoundRect(380f, 135f, 555f, 185f, 8f, 8f, paint)

        paint.color = statusTextColor
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("STATUS: ${status.uppercase()}", 467.5f, 164f, paint)
        paint.textAlign = Paint.Align.LEFT

        // 4. Line Items Table Header
        y = 240f
        paint.color = Color.parseColor("#E0D6EC")
        canvas.drawRect(40f, y, 555f, y + 26f, paint)

        paint.color = Color.parseColor("#2A2233")
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("DESCRIPTION", 50f, y + 17f, paint)
        canvas.drawText("DATE & TIME", 260f, y + 17f, paint)
        canvas.drawText("DURATION", 400f, y + 17f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT (ZAR)", 545f, y + 17f, paint)
        paint.textAlign = Paint.Align.LEFT

        // 5. Line Item Row
        y += 26f
        paint.isFakeBoldText = false
        paint.textSize = 11f
        val serviceTitle = booking.serviceName.ifBlank { "Photography Package" }
        val dateTime = "${booking.eventDate} @ ${booking.eventTime.ifBlank { "14:00" }}"
        val duration = "${booking.durationHours} Hours"
        val priceStr = "R${booking.servicePrice}.00"

        canvas.drawText(serviceTitle, 50f, y + 22f, paint)
        canvas.drawText(dateTime, 260f, y + 22f, paint)
        canvas.drawText(duration, 400f, y + 22f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(priceStr, 545f, y + 22f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Divider
        y += 36f
        paint.color = Color.parseColor("#DCD4E8")
        canvas.drawLine(40f, y, 555f, y, paint)

        // 6. Total Row
        y += 24f
        paint.color = Color.parseColor("#2A2233")
        paint.isFakeBoldText = true
        paint.textSize = 13f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL AMOUNT:  $priceStr", 545f, y, paint)
        paint.textAlign = Paint.Align.LEFT

        // 7. Studio Banking Details Box
        y += 45f
        paint.color = Color.parseColor("#F4F1F8")
        canvas.drawRoundRect(40f, y, 555f, y + 130f, 8f, 8f, paint)

        paint.color = Color.parseColor("#7E57C2")
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("STUDIO BANKING & PAYMENT DETAILS", 55f, y + 24f, paint)

        paint.color = Color.parseColor("#333333")
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Bank: ${BusinessConfig.bank.bankName}", 55f, y + 44f, paint)
        canvas.drawText("Account Holder: ${BusinessConfig.bank.accountHolder}", 55f, y + 60f, paint)
        canvas.drawText("Account Number: ${BusinessConfig.bank.accountNumber}", 55f, y + 76f, paint)
        canvas.drawText("Branch Code: ${BusinessConfig.bank.branchCode}", 55f, y + 92f, paint)

        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#7E57C2")
        canvas.drawText("Payment Reference: $invNum", 55f, y + 112f, paint)

        // 8. Terms & Policy Notes
        y += 155f
        paint.color = Color.parseColor("#666666")
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("• A 50% deposit is required to reserve the photoshoot booking slot.", 40f, y, paint)
        canvas.drawText("• Balance is payable upon photoshoot completion before release of final high-resolution gallery.", 40f, y + 14f, paint)
        canvas.drawText("• Rescheduling must be arranged at least 48 hours prior to the booked session.", 40f, y + 28f, paint)

        // 9. Footer
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        paint.color = Color.parseColor("#888888")
        canvas.drawText("Thank you for choosing Ily Photo Studio! Capturing Every Moment in Cape Town.", 297.5f, 800f, paint)

        pdfDocument.finishPage(page)

        // Save PDF to device Downloads directory
        val cleanInv = invNum.replace("[^a-zA-Z0-9-]".toRegex(), "_")
        val fileName = "ILY_Photo_Invoice_$cleanInv.pdf"

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "invoices")
        dir.mkdirs()
        val targetFile = File(dir, fileName)

        return try {
            val fos = FileOutputStream(targetFile)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDocument.close()

            // Also attempt to copy to public Downloads folder for user convenience
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownloads.exists() && publicDownloads.canWrite()) {
                    val publicFile = File(publicDownloads, fileName)
                    targetFile.copyTo(publicFile, overwrite = true)
                    MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf("application/pdf"), null)
                }
            } catch (e: Exception) {
                // Scoped storage fallback
            }

            // Notify MediaScanner
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("application/pdf"), null)

            // Trigger system alert
            IlyNotificationHelper.showNotification(
                context,
                "Invoice Saved to Device",
                "Downloaded $fileName to your device storage. Tap to view.",
                201
            )

            // Launch Intent to open PDF
            openPdfFile(context, targetFile)

            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun openPdfFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.example.ilyphoto.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Invoice saved to: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}
