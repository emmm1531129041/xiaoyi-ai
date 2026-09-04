# 小艺默认知识库资料

将需要作为知识库的资料放在本目录或子目录中，首次启动时会自动执行 ETL：

- 图片：`.jpg`、`.jpeg`、`.png`、`.webp`、`.gif`（由视觉模型提取文字与要点）
- 文本：`.txt`、`.md`、`.csv`、`.json`
- 文档：`.pdf`、`.doc`、`.docx`、`.ppt`、`.pptx`、`.html`（由 Apache Tika 提取）

向量会保存到本机 Redis Stack 中，而不是项目文件。首次切换 Redis 时会使用新的导入记录重新导入默认资料。旧的 `data/xiaoyi-vectors.json` 仅作为 SimpleVectorStore 阶段的本地备份保留。