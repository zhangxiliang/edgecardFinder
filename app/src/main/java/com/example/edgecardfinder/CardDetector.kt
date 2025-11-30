package com.example.edgecardfinder

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import kotlin.math.exp

class CardDetector(context: Context) : Closeable {

    // 1. 模型相关配置
    private val modelName = "bank_card_model.tflite" // 确保文件名一致
    private var interpreter: Interpreter? = null
    
    // 2. 图片处理相关
    private val inputSize = 224 // MobileNetV2 标准尺寸
    private val imageProcessor: ImageProcessor

    init {
        // 加载模型文件
        val modelFile = FileUtil.loadMappedFile(context, modelName)
        val options = Interpreter.Options()
        // 如果想用 NPU 加速，可以在这里 addDelegate(NnApiDelegate())
        // 但为了兼容性，先用 CPU 跑，对于量化模型也很快
        interpreter = Interpreter(modelFile, options)

        // 初始化图片处理器
        // MobileNetV2 要求输入范围是 [-1, 1]
        // 原始 Bitmap 是 [0, 255]
        // 公式：(pixel - 127.5) / 127.5
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f)) 
            .build()
    }

    /**
     * 推理函数
     * @return Pair<Boolean, Float>: <是否是银行卡, 置信度概率(0~1)>
     */
    fun detect(bitmap: Bitmap): Pair<Boolean, Float> {
        if (interpreter == null) return Pair(false, 0f)

        // A. 预处理图片
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // B. 准备输出容器
        // 根据 Python 脚本：layers.Dense(1)，输出形状是 [1, 1]
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)

        // C. 运行推理
        interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // D. 解析结果
        val outputValue = outputBuffer.floatArray[0] // 这是一个 Logit 值，可能是负数或正数

        // 将 Logit 转换为概率 (Sigmoid 函数)
        // Probability = 1 / (1 + e^(-x))
        val probability = 1.0f / (1.0f + exp(-outputValue))

        // E. 判定逻辑
        // 之前脚本逻辑：Class 0 是 bank_card，Class 1 是 others
        // Logit < 0 (或 概率 < 0.5) 倾向于 Class 0
        // Logit > 0 (或 概率 > 0.5) 倾向于 Class 1
        
        val isBankCard = outputValue < 0 // 或者 probability < 0.5
        
        // 计算如果是银行卡的置信度 (如果是 Class 0，置信度是 1 - probability)
        val confidence = if (isBankCard) (1.0f - probability) else probability

        return Pair(isBankCard, confidence)
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}