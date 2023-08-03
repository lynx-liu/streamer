#!/system/bin/sh

# 获取屏幕分辨率的输出
screen_info=$(wm size)

# 获取最后一行，其中包含分辨率信息
last_line=$(echo "$screen_info" | tail -n1)

# 从最后一行中提取宽度和高度
width=$(echo "$last_line" | grep -oE '([0-9]+)x[0-9]+' | cut -d "x" -f 1)
height=$(echo "$last_line" | grep -oE '([0-9]+)x[0-9]+' | cut -d "x" -f 2)

# 打印原始宽和高
echo "getSize: $width x $height"

# 计算新的宽度和高度
if [ $width -lt $height ]; then
    min_dimension=$width
else
    min_dimension=$height
fi

if [ $min_dimension -gt 1080 ]; then
    new_width=$((width * 1080 / min_dimension))
    new_height=$((height * 1080 / min_dimension))

    # 设置新的屏幕分辨率
    wm size ${new_width}x${new_height}
    echo "setSize: $new_width x $new_height"
fi
