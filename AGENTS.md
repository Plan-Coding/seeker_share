# AGENTS.md — Seeker Share 项目指令

本文件由 AI 代理在仓库根目录自动加载,用于指导在**本仓库内**的开发工作。

## 项目概览

Seeker Share:基于 H2 的局域网消息/文件共享节点,内置纯前端"运维/学习工具箱"。
单机部署、数据不出本机;共享广场需登录(RBAC),公开注册关闭;工具箱匿名可用。
人类可读说明见 README.md;AI Coding 说明与变更记录见 docs/AI_CODING.md。

## 技术栈

- 后端:Java 25 · Spring Boot 4.1.1 · Spring Web MVC · Spring Security · Spring Data JPA/Hibernate · H2 · Jakarta Validation · SSE · Actuator
- 前端:Thymeleaf + 原生 JS/CSS3(工具箱**零第三方依赖**)
- 构建:Maven Wrapper(`./mvnw`),不要用系统 mvn

## 目录结构

```text
src/main/java/com/seeker/share/
├── common/       # 通用 API 响应
├── security/     # 用户、角色、权限、认证与安全配置
├── share/        # 分享模型、存储、清理和实时事件服务
└── web/          # 页面与 REST 控制器
src/main/resources/
├── static/
│   ├── css/      # app.css 共享界面 · tools.css 工具箱界面
│   └── js/       # app.js 共享逻辑 · tools.js 工具箱 · hanzi-data.js 汉字数据
└── templates/    # Thymeleaf 页面模板
src/test/java/    # 后端测试
docs/             # 文档与截图
```

## 关键约束

- 工具箱新工具:以对象注册进 `TOOLS` 数组(`id`/`cat`/`icon`/`name`/`desc`/`render(body)`),哈希路由直达 `#/tools/<id>`;**必须纯前端、零第三方依赖、数据不出本机**。
- 新增分类:先扩展 `CATEGORIES`,再添加对应 `cat` 的工具;同步更新 README 中的工具数量。
- `hanzi-data.js` 由脚本从公开数据集生成(压缩编码),**不要手改数据内容**;来源与许可见文件头注释。
- 后端:控制器薄、服务厚;所有写操作走权限校验;异常统一交给 `GlobalExceptionHandler`。
- 界面语言与文案使用简体中文。

## 技能(Skills)

项目技能存放在 **`.agents/skills/`**(Agent Skills 规范的项目级技能目录)。

采用**层层递进**组织,各层只写必要内容、上层索引下层,避免单文件堆砌:

```text
.agents/skills/
├── README.md          # L0 总览:分层结构、技能清单、如何新增/使用
├── frontend/          # L1 分类:前端(README + toolbox-tool 技能)
├── backend/           # L1 分类:后端(README + api-endpoint 技能)
└── meta/              # L1 分类:AI 协作(README + issue-workflow 技能)
```

- 技能主体为 `SKILL.md`(frontmatter `name`+`description`),深层细节放其 `references/`(L3)。
- AI 代理根据技能描述按需加载执行;完整规则见 `.agents/skills/README.md`。
- 新增技能后须同步 `.agents/skills/README.md` 技能清单与分类 README。

## 常用命令

```bash
./mvnw test                 # 后端测试
./mvnw spring-boot:run      # 本地启动(默认 8080)
node --check src/main/resources/static/js/tools.js          # 前端语法检查
node --check src/main/resources/static/js/hanzi-data.js      # 数据文件语法检查
```

## 质量门槛(提交前必须通过)

- [ ] `./mvnw test` 全部通过,无新增失败/报错
- [ ] 前端改动 `node --check` 通过;新工具路由可直达、无 JS 报错(建议无头浏览器冒烟)
- [ ] 涉及数据文件时核对来源与许可注释
- [ ] README / 文档与功能一致(工具数量、目录结构、配置项)
- [ ] 未引入第三方前端依赖

## 提交规范

- Conventional Commits:`feat:` / `fix:` / `docs:` / `refactor:` / `test:` 等
- 提交信息用中文或英文均可,需简要说明改动与验证结果
- 使用 `git add -A` 后提交,并推送到 `origin/main`
