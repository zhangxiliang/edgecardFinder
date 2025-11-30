from bing_image_downloader import downloader
import os
import shutil

# 1. 定义图片类别
categories = ["人物自拍", "风景", "聚餐", "聚会", "猫咪", "狗", "家庭", "旅行", "美食", "运动"]

# 2. 每个类别的下载数量
download_limit_per_category = 10

# 3. 定义数据目录和最终的 "others" 目录
data_dir = 'data'
others_dir = os.path.join(data_dir, 'others')

# 4. 确保 "others" 文件夹存在
if not os.path.exists(others_dir):
    os.makedirs(others_dir)
    print(f"创建文件夹: {others_dir}")

# 5. 初始化一个全局计数器，用于重命名图片以避免文件名冲突
file_counter = 1

# 6. 循环遍历每个类别
for category in categories:
    print(f"--- 开始下载类别: {category} ---")
    
    # 下载图片到一个以类别命名的临时文件夹中 (例如 'data/人物自拍')
    downloader.download(
        category,
        limit=download_limit_per_category,
        output_dir=data_dir,
        adult_filter_off=True,
        force_replace=False,
        timeout=60
    )
    
    # 7. 将下载的图片移动并重命名到 'others' 文件夹
    temp_download_dir = os.path.join(data_dir, category)
    
    if os.path.exists(temp_download_dir):
        downloaded_files = [f for f in os.listdir(temp_download_dir) if os.path.isfile(os.path.join(temp_download_dir, f))]
        print(f"正在移动 {len(downloaded_files)} 张图片从 {temp_download_dir} 到 {others_dir}...")

        for filename in downloaded_files:
            source_path = os.path.join(temp_download_dir, filename)
            
            # 获取文件扩展名，如果不存在则默认为.jpg
            _, extension = os.path.splitext(filename)
            if not extension:
                extension = '.jpg'

            # 创建新的、唯一的文件名并移动文件
            destination_path = os.path.join(others_dir, f"other_{file_counter:04d}{extension}")
            shutil.move(source_path, destination_path)
            file_counter += 1
            
        # 8. 删除临时的、现已为空的类别文件夹
        try:
            shutil.rmtree(temp_download_dir)
            print(f"已删除临时文件夹: {temp_download_dir}")
        except OSError as e:
            print(f"删除文件夹失败 {temp_download_dir}: {e}")
    else:
        print(f"警告: 找不到下载目录 {temp_download_dir}。可能是下载失败或该类别没有图片。")

    print(f"--- 类别 '{category}' 处理完毕 ---\n")

print(f"所有图片下载并移动完成。总共有 {file_counter - 1} 张图片在 {others_dir} 文件夹中。")
