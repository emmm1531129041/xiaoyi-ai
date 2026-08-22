# 小艺默认知识库资料

将需要作为知识库的资料放在本目录或子目录中，首次启动时会自动执行 ETL：

- 图片：`.jpg`、`.jpeg`、`.png`、`.webp`、`.gif`（由视觉模型提取文字与要点）
- 文本：`.txt`、`.md`、`.csv`、`.json`
- 文档：`.pdf`、`.doc`、`.docx`、`.ppt`、`.pptx`、`.html`（由 Apache Tika 提取）

首次成功入库后，向量会保存到项目根目录的 `data/xiaoyi-vectors.json`。新增默认资料后，如需重新构建这个练习用知识库，请先停止项目并删除该 JSON 文件，然后重新启动。