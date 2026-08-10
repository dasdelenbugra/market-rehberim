package com.marketrehberim.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Kameradan tam çözünürlüklü kare alma.
 *
 * Eskiden `ActivityResultContracts.TakePicturePreview` kullanılıyordu; o
 * sözleşme fotoğrafın kendisini değil, kameranın **küçük önizleme karesini**
 * döndürür. ML Kit bu boyutta detayı göremiyordu: ürün etiketleme genel bir
 * kategoriye ("Food") düşüyor, raf etiketindeki fiyat ise hiç okunamıyordu.
 *
 * Kare önce dosyaya yazılır, buradan **ölçeklenerek** okunur. Ham hâlde
 * okumak seçenek değil: 50 MP'lik bir kare bellekte ~200 MB tutar ve
 * uygulamayı düşürür.
 */
object PhotoCapture {

    /**
     * Uzun kenar için üst sınır. 1600 px, ML Kit'in metin tanıma için önerdiği
     * çözünürlüğün üstünde kalır; daha yükseği doğruluk katmadan bellek yer.
     */
    private const val MAX_EDGE = 1600

    /**
     * Kameranın yazacağı geçici dosya. Tek bir ad kullanılıyor: dosya yalnız
     * bir sonraki tanımaya kadar yaşıyor, her çekimde üzerine yazılması
     * önbellekte çöp birikmesini de engelliyor.
     */
    fun newImageFile(context: Context): File = File(context.cacheDir, "capture.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Dosyayı en fazla [MAX_EDGE] piksel kenarla okur ve EXIF'teki dönüşü uygular.
     *
     * Dönüş şart: telefon dikey tutulurken çekilen kare sensörden çoğu zaman
     * yatay çıkar ve gerçek yön yalnız EXIF etiketinde durur. Uygulanmazsa
     * ML Kit'e yan bir görüntü gider — metin satırları hiç yakalanmaz.
     *
     * @return okunamayan/bozuk dosyada `null`.
     */
    fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return rotateToUpright(bitmap, file)
    }

    /** `inSampleSize` yalnız 2'nin kuvvetlerinde etkili; en yakın kuvvete çıkılır. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_EDGE) sample *= 2
        return sample
    }

    private fun rotateToUpright(bitmap: Bitmap, file: File): Bitmap {
        // EXIF okunamazsa (etiketsiz veya bozuk dosya) döndürmeden devam et:
        // yanlış yön, tanımayı hiç denememekten iyidir.
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
