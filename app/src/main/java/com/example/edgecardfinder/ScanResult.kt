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

    /**
     * 使用 Flow 返回结果，这样 UI 层可以实时收集，而不是等跑完 10000 张才显示
     */
    fun scanAllImages(): Flow<ScanResult> = flow {
        // 1. 初始化 AI 引擎 (放在 IO 线程)
        val detector = CardDetector(context)
        
        // 2. 查询相册所有图片
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID)
        // 按时间倒序，先看最新的
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC" 

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            
            // 3. 循环遍历 (处理一万张图的核心循环)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                
                var bitmap: Bitmap? = null
                try {
                    // === 关键点：只加载缩略图 ===
                    // 直接请求 224x224 的大小，Android 系统底层会帮我们做降采样
                    // 这样内存占用极小，速度极快
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bitmap = context.contentResolver.loadThumbnail(
                            contentUri,
                            Size(224, 224),
                            null
                        )
                    } else {
                        // 旧版本兼容写法 (略微复杂点，需要用 MediaStore.Images.Thumbnails)
                        // 这里为了代码简洁，假设运行在 Android Q 以上
                        // 实际开发建议用 Glide 加载成 Bitmap: 
                        // Glide.with(ctx).asBitmap().load(uri).submit(224, 224).get()
                    }

                    if (bitmap != null) {
                        // 4. 执行 AI 推理
                        val (isCard, score) = detector.detect(bitmap)

                        // 5. 如果是银行卡，发射结果给 UI
                        if (isCard && score > 0.8f) { // 阈值设为 0.8，过滤掉似是而非的
                            emit(ScanResult(contentUri.toString(), true, score))
                            Log.d("EdgeAI", "发现银行卡: $contentUri, 置信度: $score")
                        }
                    }

                } catch (e: IOException) {
                    // 图片损坏，跳过
                    e.printStackTrace()
                } finally {
                    // === 关键点：立即回收内存 ===
                    // 虽然 GC 会自动回收，但在高频循环中，手动 recycle 是防抖动的最佳实践
                    bitmap?.recycle() 
                }
            }
        }
        
        // 6. 任务结束，关闭模型
        detector.close()
        
    }.flowOn(Dispatchers.IO) // 确保全过程在 IO 线程运行，不卡顿 UI
}