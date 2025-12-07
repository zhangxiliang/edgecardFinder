package com.example.edgecardfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.edgecardfinder.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanResultAdapter: ScanResultAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startScan()
            } else {
                binding.textEmptyState.text = "需要存储权限才能扫描相册"
                binding.textEmptyState.visibility = View.VISIBLE
                Snackbar.make(binding.root, "扫描功能需要文件读取权限", Snackbar.LENGTH_LONG).show()
            }
        }

    private val requiredPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE // Android 12 及以下
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        scanResultAdapter = ScanResultAdapter()
        binding.recyclerViewResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = scanResultAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            checkPermissionAndStartScan()
        }
    }

    private fun checkPermissionAndStartScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                requiredPermission
            ) == PackageManager.PERMISSION_GRANTED -> {
                startScan()
            }
            else -> {
                requestPermissionLauncher.launch(requiredPermission)
            }
        }
    }

    private fun startScan() {
        lifecycleScope.launch {
            val scanner = GalleryScanner(applicationContext)

            Log.d("MainActivity", "开始扫描相册...")

            scanner.scanAllImages()
                .onStart {
                    scanResultAdapter.clearResults()
                    binding.progressBar.visibility = View.VISIBLE
                    binding.textEmptyState.visibility = View.GONE
                    binding.recyclerViewResults.visibility = View.GONE
                    binding.btnScan.isEnabled = false
                }
                .onCompletion { exception ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnScan.isEnabled = true
                    if (exception != null) {
                        Log.e("MainActivity", "扫描因异常结束", exception)
                        binding.textEmptyState.text = "扫描出错: ${exception.message}"
                        binding.textEmptyState.visibility = View.VISIBLE
                    } else {
                        Log.d("MainActivity", "扫描正常完成.")
                        if (scanResultAdapter.itemCount == 0) {
                            binding.textEmptyState.text = "扫描完成，未发现银行卡"
                            binding.textEmptyState.visibility = View.VISIBLE
                        }
                    }
                }
                .collect { result ->
                    binding.recyclerViewResults.visibility = View.VISIBLE
                    scanResultAdapter.addResult(result)
                    // [修复] 滚动到新添加的项的顶部 (位置 0)
                    binding.recyclerViewResults.scrollToPosition(0)
                }
        }
    }
}
