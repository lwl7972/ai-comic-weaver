---
name: pil-diagram
description: "使用 Python PIL 生成简单的中文文本图表/流程图/示意图——输入文本描述，输出 PNG 图片。适用于快速生成架构图、流程图、对比表等。"
---

# PIL 图表生成器

使用 Python PIL (Pillow) 库快速生成包含中文文本的简单图表。

## 适用场景

- 生成架构图、模块关系图
- 生成流程图、状态机图
- 生成对比表、决策矩阵
- 生成简单的拓扑图、部署图
- 任何需要快速可视化文本信息的场景

## 执行流程

### 1. 分析用户需求

理解用户想要生成什么类型的图表，确定：
- 图表类型（流程图、架构图、对比表等）
- 内容要素（节点、连线、文字说明）
- 大致尺寸（根据内容量估算）

### 2. 编写 Python 脚本

使用以下模板作为起点，根据需求调整：

```python
# -*- coding: utf-8 -*-
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from PIL import Image, ImageDraw, ImageFont

# 中文字体路径（Windows）
FONT_PATH = 'C:/Windows/Fonts/msyh.ttc'

# 创建画布（根据内容调整尺寸）
W, H = 800, 600
img = Image.new('RGB', (W, H), (255, 255, 255))
d = ImageDraw.Draw(img)

# 加载字体
title_font = ImageFont.truetype(FONT_PATH, 24)
body_font = ImageFont.truetype(FONT_PATH, 16)

# 绘制标题
d.text((W//2, 30), '图表标题', fill='black', font=title_font, anchor='mt')

# 绘制内容（根据图表类型自由发挥）
# ...

# 保存
output_path = 'output_diagram.png'
img.save(output_path, quality=95)
print(f'已保存: {output_path}')
```

### 3. 常用绘图模式

**矩形节点：**
```python
x, y, w, h = 100, 100, 200, 60
d.rounded_rectangle([x, y, x+w, y+h], radius=10, fill='#E3F2FD', outline='#1565C0', width=2)
d.text((x + w//2, y + h//2), '节点文字', fill='black', font=body_font, anchor='mm')
```

**连线/箭头：**
```python
d.line([(x1, y1), (x2, y2)], fill='#666666', width=2)
# 箭头
d.polygon([(x2, y2), (x2-10, y2-5), (x2-10, y2+5)], fill='#666666')
```

**背景色块：**
```python
d.rounded_rectangle([x, y, x+w, y+h], radius=8, fill='#F5F5F5', outline='#CCCCCC')
```

### 4. 保存并交付

- 输出路径：`D:\AI\atomgit\output\` 或用户指定路径
- 格式：PNG，quality=95
- 确保文件名有意义且不覆盖已有文件

## 注意事项

- 字体使用 `msyh.ttc`（微软雅黑），确保中文显示正常
- 画布尺寸根据内容量自动调整，留足边距
- 颜色方案保持简洁专业，避免过多颜色
- 如果图表复杂，考虑分层绘制（背景层 → 连线层 → 节点层 → 文字层）
- 运行脚本时确保工作目录正确（避免路径问题）
