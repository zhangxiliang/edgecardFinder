package com.example.edgecardfinder

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.edgecardfinder.databinding.ListItemResultBinding

class ScanResultAdapter(private val results: MutableList<ScanResult> = mutableListOf()) :
    RecyclerView.Adapter<ScanResultAdapter.ResultViewHolder>() {

    // ViewHolder: 缓存列表项的视图
    class ResultViewHolder(val binding: ListItemResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ListItemResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        holder.binding.apply {
            // 设置图片
            // 注意：在实际项目中，强烈建议使用 Glide 或 Coil 库来加载图片，
            // 它们能高效处理缓存、解码和显示，避免内存问题。
            imageViewThumbnail.setImageURI(Uri.parse(result.uriString))

            // 设置置信度分数
            textViewScore.text = String.format("置信度: %.2f%%", result.score * 100)
            
            // 设置图片路径
            textViewUri.text = result.uriString
        }
    }

    override fun getItemCount() = results.size

    /**
     * 添加一个新的扫描结果到列表顶部
     */
    fun addResult(newResult: ScanResult) {
        results.add(0, newResult)
        notifyItemInserted(0)
    }

    /**
     * 清空所有结果
     */
    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }
}