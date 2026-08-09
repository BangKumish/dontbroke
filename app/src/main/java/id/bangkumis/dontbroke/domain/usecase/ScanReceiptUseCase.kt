package id.bangkumis.dontbroke.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import id.bangkumis.dontbroke.BuildConfig
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.network.api.HF_VISION_MODEL
import id.bangkumis.dontbroke.network.api.HuggingFaceApi
import id.bangkumis.dontbroke.network.model.ChatMessage
import id.bangkumis.dontbroke.network.model.ChatRequest
import id.bangkumis.dontbroke.network.model.ImagePart
import id.bangkumis.dontbroke.network.model.ImageUrl
import id.bangkumis.dontbroke.network.model.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Longest edge after downscaling. */
const val MAX_IMAGE_PX = 1024

/** Upload ceiling; quality steps down until the JPEG fits. */
const val MAX_IMAGE_BYTES = 500 * 1024

private const val START_QUALITY = 80

private val SCAN_PROMPT = """
    You are an expert Indonesian financial receipt and QRIS parser. Extract transaction details from this image. Return raw JSON strictly matching this structure: {"amount": 15000.0, "category": "Makanan & Minuman", "location": "Bakmi GM", "suggestedAccount": "ShopeePay"}. Map category names to standard options (Makanan & Minuman, Transportasi, Belanja, Tagihan, Hiburan, Lainnya). If a field is uncertain, return null.
""".trimIndent()

/** A scan either produced fields or has something to say about why it did not. */
sealed interface ScanOutcome {
    data class Success(val result: ParsedReceiptResult) : ScanOutcome
    data class Failure(val message: String) : ScanOutcome
}

/**
 * Reads a receipt or QRIS photo with the Hugging Face vision model.
 *
 * The image is downscaled and JPEG-compressed on device: a modern camera photo is
 * several MB, and base64 inflates it another third, which is a slow upload and a
 * likely request-size rejection.
 */
class ScanReceiptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ai: HuggingFaceApi,
) {

    suspend operator fun invoke(
        uri: Uri,
        type: TransactionType = TransactionType.EXPENSE,
    ): ScanOutcome = withContext(Dispatchers.IO) {
        val bitmap = runCatching { decodeSampled(uri) }.getOrNull()
            ?: return@withContext ScanOutcome.Failure("Gagal membuka gambar.")
        invoke(bitmap, type)
    }

    suspend operator fun invoke(
        bitmap: Bitmap,
        type: TransactionType = TransactionType.EXPENSE,
    ): ScanOutcome = withContext(Dispatchers.IO) {
        if (BuildConfig.HF_API_KEY.isBlank()) {
            return@withContext ScanOutcome.Failure("HF_API_KEY belum diatur di local.properties.")
        }
        val base64 = runCatching { toCompressedBase64(bitmap) }.getOrNull()
            ?: return@withContext ScanOutcome.Failure("Gagal memproses gambar.")

        val reply = runCatching {
            ai.chat(
                ChatRequest(
                    model = HF_VISION_MODEL,
                    messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = listOf(
                                ImagePart(ImageUrl("data:image/jpeg;base64,$base64")),
                                TextPart(SCAN_PROMPT),
                            )
                        )
                    ),
                    // enough for the JSON object; the prompt asks for nothing else
                    maxTokens = 300,
                )
            ).content()
        }.getOrElse { e ->
            // A cancelled scan has no failure to report — and reporting one would
            // write a message into a state nobody is watching. Rethrow so the
            // coroutine actually dies: kotlinx's CancellationException is an
            // IllegalStateException, so runCatching catches it like any other.
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ScanReceipt", "vision request failed", e)
            return@withContext ScanOutcome.Failure(errorMessage(e))
        }

        val parsed = parseReceiptJson(reply, type)
            ?: return@withContext ScanOutcome.Failure("Struk tidak terbaca — coba foto yang lebih jelas.")
        if (parsed.isEmpty) {
            return@withContext ScanOutcome.Failure("Tidak ada detail yang terbaca — isi manual saja.")
        }
        ScanOutcome.Success(parsed)
    }

    /** Same taxonomy as the insight card, phrased for this screen. */
    private fun errorMessage(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> "API key ditolak — periksa HF_API_KEY."
            429 -> "Terlalu banyak permintaan — coba lagi sebentar lagi."
            503 -> "Model sedang dimuat — coba lagi sebentar lagi."
            else -> "Gagal memindai (HTTP ${e.code()})."
        }
        is java.net.UnknownHostException -> "Tidak ada koneksi internet."
        is java.net.SocketTimeoutException -> "Waktu permintaan habis — coba lagi."
        else -> "Gagal memindai: ${e.message ?: e::class.simpleName}"
    }

    /** Two passes: bounds first, so the full-size bitmap is never allocated. */
    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMAGE_PX)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /**
     * Scale to [MAX_IMAGE_PX], then drop quality until the JPEG is under
     * [MAX_IMAGE_BYTES]. Receipts are text on white, so they survive it.
     */
    private fun toCompressedBase64(source: Bitmap): String {
        val scaled = downscale(source)
        var quality = START_QUALITY
        var bytes = scaled.toJpeg(quality)
        while (bytes.size > MAX_IMAGE_BYTES && quality > 30) {
            quality -= 15
            bytes = scaled.toJpeg(quality)
        }
        if (scaled !== source) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_IMAGE_PX) return bitmap
        val ratio = MAX_IMAGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
