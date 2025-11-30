import os
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

# =================配置区域=================
# 图片文件夹的根目录（里面包含 id_card 和 others 两个子文件夹）
DATA_DIR = './data'
# 模型输入尺寸 (MobileNetV2 标准尺寸)
IMG_SIZE = (224, 224)
# 批次大小 (每次训练喂给模型 32 张图)
BATCH_SIZE = 32
# 训练轮数 (看几遍数据，数据少的话 5-10 轮够了)
EPOCHS = 10
# 输出文件名
OUTPUT_MODEL_NAME = 'id_card_model.tflite'

print("--- 1. 开始加载数据 ---")
# 使用 Keras 的工具自动从文件夹加载数据
# validation_split=0.2 意思是切分 20% 的数据用来做考试（验证集），不参与训练
train_ds = tf.keras.utils.image_dataset_from_directory(
    DATA_DIR,
    validation_split=0.2,
    subset="training",
    seed=123,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE
)

val_ds = tf.keras.utils.image_dataset_from_directory(
    DATA_DIR,
    validation_split=0.2,
    subset="validation",
    seed=123,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE
)

# 获取分类名称，打印出来确认一下 (比如 ['id_card', 'others'])
class_names = train_ds.class_names
print(f"识别到的类别: {class_names}")

# =================数据增强 (关键步骤)=================
# 应对你说的“角度歪、背景乱”的问题
data_augmentation = keras.Sequential(
    [
        layers.RandomFlip("horizontal_and_vertical"), # 随机翻转
        layers.RandomRotation(0.2), # 随机旋转 20%
        layers.RandomZoom(0.1),     # 随机缩放
        layers.RandomContrast(0.2), # 随机调整对比度
    ]
)

print("--- 2. 构建模型 (迁移学习) ---")
# 下载 MobileNetV2 (预训练模型)，去掉顶部的分类层 (include_top=False)
# 就像借用一个认识万物的大脑，但把最后的嘴巴封住
base_model = tf.keras.applications.MobileNetV2(
    input_shape=IMG_SIZE + (3,),
    include_top=False,
    weights='imagenet'
)
base_model.trainable = False # 冻结它，不要打乱它原本学到的知识

# 组装新模型
inputs = tf.keras.Input(shape=IMG_SIZE + (3,))
# 1. 预处理：把像素值转为 [-1, 1] 之间，这是 MobileNet 要求的
x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
# 2. 数据增强
x = data_augmentation(x)
# 3. 传入预训练模型
x = base_model(x, training=False)
# 4. 把多维数据压扁
x = layers.GlobalAveragePooling2D()(x)
# 5. 防止过拟合 (Drop 20% 的神经元)
x = layers.Dropout(0.2)(x)
# 6. 最终输出层：1个节点 (输出概率 score)
# 它是二分类，输出一个数值，<0 是类别0，>0 是类别1 (经过 Sigmoid 后变成 0-1)
outputs = layers.Dense(1)(x)

model = keras.Model(inputs, outputs)

# 编译模型
model.compile(optimizer='adam',
              loss=tf.keras.losses.BinaryCrossentropy(from_logits=True),
              metrics=['accuracy'])

print("--- 3. 开始训练 ---")
history = model.fit(train_ds, epochs=EPOCHS, validation_data=val_ds)

print("--- 4. 转换并导出为 TFLite (包含量化) ---")

# 转换器
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# === 关键优化：动态范围量化 (Dynamic Range Quantization) ===
# 这会让模型体积缩小 4 倍 (比如 10MB -> 2.5MB)，且几乎不损失精度
converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()

# 保存文件
with open(OUTPUT_MODEL_NAME, 'wb') as f:
    f.write(tflite_model)

print(f"成功！模型已保存为: {OUTPUT_MODEL_NAME}")
print(f"类别对应关系: 0 -> {class_names[0]}, 1 -> {class_names[1]}")
print("请根据这个对应关系在 Android 代码里写逻辑。")