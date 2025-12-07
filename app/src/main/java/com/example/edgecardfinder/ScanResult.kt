package com.example.edgecardfinder

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

data class ScanResult(val uriString: String, val isCard: Boolean, val score: Float)

class GalleryScanner(private val context: Context) {

    private val TAG = "GalleryScanner"

    fun scanAllImages(): Flow<ScanResult> = flow {
        val detector = CardDetector(context)
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            
            Log.d(TAG, "查询到 ${cursor.count} 张图片，开始逐一扫描...")

            var processedCount = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                
                var bitmap: Bitmap? = null
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bitmap = context.contentResolver.loadThumbnail(
                            contentUri,
                            Size(224, 224),
                            null
                        )
                    } else {
                        // 实际项目建议适配旧版本或使用 Glide
                    }

                    if (bitmap != null) {
                        val (isCard, score) = detector.detect(bitmap)

                        // [日志] 打印每一张图的识别结果
                        Log.i(TAG, "处理图片 #${processedCount}: $contentUri -> isCard: $isCard, Score: $score")

                        // [诊断] 暂时降低阈值，观察是否有更多结果
                        if (isCard && score > 0.5f) { 
                            emit(ScanResult(contentUri.toString(), true, score))
                            Log.d(TAG, "发现疑似银行卡: $contentUri, 置信度: $score")
                        }
                        processedCount++
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "处理图片失败: $contentUri", e)
                } finally {
                    bitmap?.recycle()
                }
            }
        }
        
        detector.close()
        
    }.flowOn(Dispatchers.IO)
}