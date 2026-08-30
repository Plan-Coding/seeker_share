# Seeker Share · AI Coding 说明

> 本文件说明本项目如何使用 AI 参与开发、遵循的工程约定，以及如何通过 GitHub Issue 与 AI 协作。

## 1. 项目定位

Seeker Share 是一个**基于 H2 的局域网消息与文件共享节点**，内置一套**零后端依赖的纯前端运维/学习工具箱**。项目采用单机部署，数据不出本机，共享广场需登录、公开注册关闭，工具箱保持匿名可用。

AI 在本项目中承担了**架构设计、功能实现、界面设计、测试与文档维护**等全流程工作，具体能力边界与协作方式见下文。

## 2. AI 参与范围

| 环节 | AI 参与内容 | 说明 |
| --- | --- | --- |
| 架构设计 | 分层结构、RBAC 权限模型、SSE 实时同步、存储与过期策略 | 单机可维护、易测试 |
| 后端实现 | 共享/存储/安全/管理 REST API、Spring Security 鉴权 | 所有共享 API 均由后端强制校验 |
| 前端实现 | 共享广场、运维工具箱、权限管理的界面与交互 | 工具箱全部在浏览器本地运行 |
| 算法实现 | MD5/SHA-1/SHA-256/CRC32、Cron 解析、CIDR 计算、LCS diff 等 | 纯 JS 实现，无外部依赖 |
| 数据工程 | 生成压缩编码的汉字数据集 `hanzi-data.js`（拼音 + 笔画数） | 数据来源与许可见第 4.3 节 |
| 测试 | 后端 JUnit 测试、前端语法与运行时冒烟验证 | 见第 5 节 |
| 文档 | README、本文件、UI 截图维护 | — |

## 3. 工程约定

### 3.1 技术栈

- **后端**：Java 25 · Spring Boot 4.1.1 · Spring Web MVC · Spring Security · Spring Data JPA / Hibernate · H2 · Jakarta Validation · SSE · Actuator
- **前端**：Thymeleaf + 原生 JavaScript / CSS3（工具箱**零第三方依赖**）
- **构建**：Maven Wrapper（`./mvnw`）

### 3.2 目录结构

```text
src/main/java/com/seeker/share/
├── common/       # 通用 API 响应
├── security/     # 用户、角色、权限、认证与安全配置
├── share/        # 分享模型、存储、清理和实时事件服务
└── web/          # 页面与 REST 控制器
src/main/resources/
├── static/
│   ├── css/      # app.css 共享界面 · tools.css 工具箱界面
│   └── js/       # app.js 共享逻辑 · tools.js 工具箱（纯前端）· hanzi-data.js 汉字拼音/笔画数据
└── templates/    # Thymeleaf 页面模板
src/test/java/    # 后端测试
docs/             # 文档（含本文件、UI 截图）
```

### 3.3 编码规范

- **后端**：按 `common / security / share / web` 分层；控制器薄、服务厚；所有写操作走权限校验；异常统一交给 `GlobalExceptionHandler`。
- **前端工具箱**：新工具以对象注册进 `TOOLS` 数组（含 `id`、`cat` 分类、`icon`、`name`、`desc`、`render(body)`），路由用哈希直达 `#/tools/<id>`；工具必须**纯前端实现、数据不出本机**。
- **新增分类**：先扩展 `CATEGORIES` 数组，再向 `TOOLS` 添加对应 `cat` 的工具。
- **数据文件**：`hanzi-data.js` 由脚本从公开数据集生成（压缩编码），**不要手改数据内容**；如需扩充请重新生成并同步文件头注释。

### 3.4 数据与许可

`hanzi-data.js` 数据来源：

- 拼音与多音字（按使用频率排序）：[mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data)（MIT）
- 笔画数：Unicode UNIHAN `kTotalStrokes`

文件头已注明来源与编码方式（`HANZI_CHARS` 位置 i 对应 `HANZI_PY` 两字符 base62 读音下标、`HANZI_STROKES` 单字符笔画数）。**商用/再分发前请核对上游许可条款。**

## 4. 与 AI 协作：Issue 驱动工作流

如果你在使用中遇到 Bug、兼容性问题，或有新的功能建议，请前往 [GitHub Issues](https://github.com/Plan-Coding/seeker_share/issues) 提交反馈。Issue 将通过 AI 辅助分析、修复和完善，并在提交前进行必要的代码检查与测试。

### 4.1 提交 Issue 的建议模板

```text
【问题现象 / 功能建议】
（描述具体现象、期望行为）

【复现步骤】
1. ...

【环境】
- 操作系统：Windows / macOS / Linux
- Java 版本：JDK 25
- 浏览器：Chrome / Edge / Firefox（含版本号）

【相关日志或截图】
（粘贴报错信息或附件）
```

提供上述信息可显著提升 AI 分析与修复的准确率。

### 4.2 Issue → 修复 → 合入流程

1. **分类**：Bug / 功能建议 / 兼容性问题 / 文档问题。
2. **分析**：AI 复现或定位问题，评估影响面。
3. **修复**：遵循第 3 节工程约定进行修改；工具箱新增工具保持纯前端、零依赖。
4. **验证**：执行第 5 节质量保障流程，全部通过后再提交。
5. **提交**：提交信息遵循 Conventional Commits（`feat:` / `fix:` / `docs:` / `refactor:` / `test:` 等），并简要说明改动与验证结果。

## 5. 质量保障与验证流程

所有改动（尤其 AI 生成）提交前必须通过：

```bash
# 1) 后端测试
./mvnw test

# 2) 前端 JS 语法检查
node --check src/main/resources/static/js/tools.js
node --check src/main/resources/static/js/hanzi-data.js

# 3) 运行时冒烟验证（可选，推荐）
# 启动应用后，用无头浏览器逐一遍历 #/tools/<id> 各工具路由，
# 确认无 JS 报错、交互（输入/切换/点击）正常。
./mvnw spring-boot:run
```

**提交前 Checklist**

- [ ] `./mvnw test` 全部通过（无新增失败/报错）
- [ ] 前端新工具 `node --check` 通过，路由可直达、无 JS 报错
- [ ] 涉及数据文件时已核对来源与许可注释
- [ ] README / 文档与功能保持一致（工具数量、目录结构、配置项）
- [ ] 未引入第三方前端依赖（工具箱保持零依赖）

## 6. AI 任务提示词模板

维护者或贡献者向 AI 提出任务时，可参考以下模板以提高协作效率：

```text
【任务】新增 / 修复 / 重构 / 文档
【目标工具】#/tools/<id>（如新增）
【功能要求】……
【约束】
- 纯前端实现，零第三方依赖
- 遵循 TOOLS 数组注册规范
- 数据不出本机
【验收标准】……
【验证方式】mvnw test / node --check / 无头浏览器冒烟
```

## 7. AI 变更记录

> 记录主要 AI 参与的功能变更，便于追溯与审计。

| 日期 | 变更 | 说明 |
| --- | --- | --- |
| 2026-08-29 | 项目初始化 | 搭建 Spring Boot + H2 + Thymeleaf 骨架 |
| 2026-08-30 | 共享广场与 RBAC | 消息/文件共享、SSE 同步、用户/角色/权限管理 |
| 2026-08-30 | 运维工具箱 | 23 个纯前端工具，哈希路由直达、分类菜单与搜索 |
| 2026-08-30 | 学习工具 | 新增字帖生成器、汉字注音、全角/半角转换，工具箱扩展至 26 个 |
| 2026-08-30 | 汉字数据集 | 新增 `hanzi-data.js`（7832 常用字拼音 + 笔画数，压缩编码） |

---

**免责声明**：本软件按“原样”提供，不作任何明示或默示的保证。AI 生成的代码可能包含缺陷，使用前请自行评估并执行必要的代码检查与测试。完整条款见 [LICENSE](../LICENSE)。
