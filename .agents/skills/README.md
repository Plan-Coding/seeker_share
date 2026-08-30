# Seeker Share · 技能目录(Skills)

本目录存放本项目可复用的 **Agent Skills**(技能包),按 [Agent Skills 规范](https://agentskills.io/specification) 组织,遵循**渐进式披露**:仅技能描述常驻上下文,完整指令按需加载。项目级技能统一存放在 `.agents/skills/`(行业通用约定)。

> **当前已安装技能:4 个**(`frontend/toolbox-tool`、`backend/api-endpoint`、`meta/issue-workflow`、`meta/commit-release`)。
> 每次新增技能后,请同步更新本文件技能清单与对应分类的 README。

## 分层结构(层层递进)

遵循「每层只写必要内容、上层索引下层」的原则,避免把细节堆进单个文件:

| 层级 | 路径 | 内容 |
| --- | --- | --- |
| L0 总览 | `README.md`(本文件) | 目录说明、分层结构、技能清单、如何新增/使用 |
| L1 分类 | `frontend/` `backend/` `meta/` 下各自的 `README.md` | 该分类的技能清单与一句话说明 |
| L2 技能 | 各分类下 `<skill>/SKILL.md` | 技能主体:frontmatter(`name`+`description`)+ 精简步骤 |
| L3 深度 | 技能目录内 `references/` 等 | 按需加载的详细文档 / 模板 / 清单 |

## 技能清单

| 技能 | 分类 | 说明 |
| --- | --- | --- |
| [toolbox-tool](frontend/toolbox-tool/SKILL.md) | frontend | 新增/修改纯前端工具箱工具(TOOLS 注册、CSS、验证) |
| [api-endpoint](backend/api-endpoint/SKILL.md) | backend | 新增受权限保护的后端 REST API(含测试) |
| [issue-workflow](meta/issue-workflow/SKILL.md) | meta | Issue 驱动流程:分析 → 修复 → 验证 → 提交 |
| [commit-release](meta/commit-release/SKILL.md) | meta | 提交/发布流程:新功能确认版本与分支、合入 main 后打 tag |

## 如何新增一个技能

1. 在对应分类下创建目录 `<name>/`,内含 `SKILL.md`,frontmatter 至少包含 `name`(小写字母/数字/连字符)与 `description`(何时使用、具体描述)。
2. 技能主体保持精简,复杂细节放入 `<name>/references/`。
3. 更新本文件技能清单与对应分类 README。
4. 规范细节见 [Agent Skills 规范](https://agentskills.io/specification)。

## 如何使用技能

AI 代理会根据技能 `description` 判断适用场景,按需加载并执行对应 `SKILL.md` 中的指令。
