---
name: toolbox-tool
description: 在 Seeker Share 工具箱中新增或修改一个纯前端小工具:注册 TOOLS 条目、添加 CSS、验证并同步 README。当需要操作 #/tools/<id> 下的工具或新增工具时使用。
---

# 新增/修改工具箱工具

## 相关文件

- 工具注册表:`src/main/resources/static/js/tools.js`(`CATEGORIES` + `TOOLS` 数组)
- 样式:`src/main/resources/static/css/tools.css`
- 汉字数据(拼音/笔画,可选):`src/main/resources/static/js/hanzi-data.js`

## 步骤

1. **分类**:若无合适分类,先向 `CATEGORIES` 追加;否则复用现有分类 `cat`。
2. **注册**:在 `TOOLS` 数组对应分类注释处新增对象,字段:
   - `id`(路由 `#/tools/<id>`)、`cat`、`icon`、`name`、`desc`
   - `render(body)`:用内置工具函数 `el/area/field/input/btn/checkbox/note/copyBtn` 构建界面
3. **样式**:需要时在 `tools.css` 添加对应类(参考现有工具的 `stat-grid`/`out-grid`/`gen-list` 等模式)。
4. **验证**:
   ```bash
   node --check src/main/resources/static/js/tools.js
   ```
   启动应用后访问 `#/tools/<id>` 确认无 JS 报错、交互正常。
5. **文档**:更新 `README.md` 的工具表与工具总数(当前 26 个)。

## 约束

- 纯前端实现、数据不出本机、不从外部网络/CDN 加载依赖(第三方库需下载到 `static/vendor/` 本地引入并保留许可证)
- 界面文案使用简体中文
- 复杂算法/数据放独立小节或引用 `references/`,保持 `render` 精简

## 参考

- [提交前检查清单](references/checklist.md)
