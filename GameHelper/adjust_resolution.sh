#!/system/bin/sh

# ��ȡ��Ļ�ֱ��ʵ����
screen_info=$(wm size)

# ��ȡ���һ�У����а����ֱ�����Ϣ
last_line=$(echo "$screen_info" | tail -n1)

# �����һ������ȡ��Ⱥ͸߶�
width=$(echo "$last_line" | grep -oE '([0-9]+)x[0-9]+' | cut -d "x" -f 1)
height=$(echo "$last_line" | grep -oE '([0-9]+)x[0-9]+' | cut -d "x" -f 2)

# ��ӡԭʼ��͸�
echo "getSize: $width x $height"

# �����µĿ�Ⱥ͸߶�
if [ $width -lt $height ]; then
    min_dimension=$width
else
    min_dimension=$height
fi

if [ $min_dimension -gt 1080 ]; then
    new_width=$((width * 1080 / min_dimension))
    new_height=$((height * 1080 / min_dimension))

    # �����µ���Ļ�ֱ���
    wm size ${new_width}x${new_height}
    echo "setSize: $new_width x $new_height"
fi
