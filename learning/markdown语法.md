[Markdown 语法大全详解_markdown语法-CSDN博客](https://blog.csdn.net/w11111xxxl/article/details/140783343)

emoji表情符号

emoji表情使用:EMOJICODE:的格式，详细列表可见

https://www.webpagefx.com/tools/emoji-cheat-sheet/

当然现在很多**Markdown**工具或者网站都不支持。

工具或网站是否支持emoji表情符号

- 简书否
- MarkDownPad否（不知道付费版是否支持）
- 有道云笔记否
- zybuluo.com否
- github是

以下是一份全面的Markdown语法笔记，涵盖基础语法、扩展语法及实用技巧：

# **Markdown 语法笔记**

## **一、基础语法**

### 1. 标题

```
# 一级标题
## 二级标题
### 三级标题
#### 四级标题
##### 五级标题
###### 六级标题
```

### 2. 段落

直接输入文本即可，段落之间空一行分隔。

### 3. 列表

- **无序列表**

```
- 项目1
- 项目2
  - 子项目
```

- **有序列表**

```
1. 步骤1
2. 步骤2
   3. 子步骤
```

### 4. 强调

```
*斜体* 或 _斜体_  
**粗体** 或 __粗体__  
~~删除线~~  
`代码块`
```

### 5. 引用

```
> 引用块  
> 多行引用  
>> 嵌套引用
```

### 6. 链接

```
[文字链接](https://example.com)  
[带标题链接](https://example.com "标题")  
[图片链接](https://example.com/image.jpg)  
[锚点链接](#目录)
```

### 7. 图片

```
![替代文本](https://example.com/image.jpg "标题")  
<!-- 调整大小（部分解析器支持） -->
![图片](image.jpg =300x200)
```

### 8. 代码块

```
​```javascript
function hello() {
  console.log("Hello, World!");
}
```

### 9. 表格

```
​```markdown
| 表头1 | 表头2 |
|-------|-------|
| 左对齐 | 右对齐 |
| 内容1 | 内容2 |
```

### 10. 水平线

```
---
***
```

## **二、高级语法**

### 1. 数学公式（LaTeX）

```
行内公式：$E=mc^2$  
块级公式：
$$
\int_{a}^{b} f(x) dx
$$
```

### 2. 流程图（Mermaid）

```
​```mermaid
graph TD
  A[开始] --> B{条件判断}
  B -->|成立| C[执行操作]
  B -->|不成立| D[结束]
```

### 3. 时序图（Mermaid）

```
​```mermaid
sequenceDiagram
  参与者 A ->> 参与者 B: 消息1
  参与者 B --> 参与者 A: 消息2
```

### 4. 任务列表（GFM）

```
- [x] 已完成任务
- [ ] 未完成任务
  - [ ] 子任务
```

### 5. 脚注

这是一个脚注[^1](脚注内容)

### 6. 目录

```
[TOC]  <!-- 自动生成目录 -->
```

## **三、实用技巧**

### 1. 转义字符

```
\* 转义星号  
\\ 转义反斜杠  
\` 转义反引号
```

### 2. 表格对齐

```
| 左对齐 | 居中 | 右对齐 |
| :----- | :--: | ----: |
| 内容1  | 内容2 | 内容3  |
```

### 3. 代码块高亮

```
​```python{highlightLines: [3,5]}
def func():
  print("Line 1")
  print("Line 2")  # 高亮行
  print("Line 3")
```

### 4. 图片居中（HTML）

```
​```markdown
<div align="center">
  <img src="image.jpg" alt="图片" width="50%">
</div>
```

### 5. 折叠内容

```
<details>
  <summary>点击展开</summary>
  隐藏的内容
</details>
```