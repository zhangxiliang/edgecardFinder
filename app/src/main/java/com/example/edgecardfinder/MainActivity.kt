package com.example.edgecardfinder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.edgecardfinder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    // 在 Activity 或 ViewModel 中
    fun startScan() {
        lifecycleScope.launch {
            val scanner = GalleryScanner(applicationContext)

            // 这一步是流式更新的，每发现一张，UI 就会更新一次
            // 不会等一万张扫完才显示
            scanner.scanAllImages().collect { result ->
                // 在这里把 result 添加到 RecyclerView 的 Adapter 里
                //myAdapter.addData(result)
                //txtLog.text = "找到一张卡片，置信度：${result.score}"
            }

            //txtLog.text = "扫描完成！"
        }
    }
}
