# Seeker Share · 技能目录(Skills)

本目录存放本项目可复用的 **Agent Skills**(技能包),按 [Agent Skills 规范](https://agentskills.io/specification) 组织。
pi 在项目被信任后会自动发现 `.agents/skills/` 下的技能,通过 `/skill:<name>` 按需加载(仅描述常驻上下文,完整指令按需读取——即"渐进式披露")。

> **当前已安装技能:3 个**(`frontend/toolbox-tool`、`backend/api-endpoint`、`meta/issue-workflow`)。
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

| 技能 | 分类 | 说明 | 调用 |
| --- | --- | --- | --- |
| [toolbox-tool](frontend/toolbox-tool/SKILL.md) | frontend | 新增/修改纯前端工具箱工具(TOOLS 注册、CSS、验证) | `/skill:toolbox-tool` |
| [api-endpoint](backend/api-endpoint/SKILL.md) | backend | 新增受权限保护的后端 REST API(含测试) | `/skill:api-endpoint` |
| [issue-workflow](meta/issue-workflow/SKILL.md) | meta | Issue 驱动流程:分析 → 修复 → 验证 → 提交 | `/skill:issue-workflow` |

## 如何新增一个技能

1. 在对应分类下创建目录 `<name>/`,内含 `SKILL.md`,frontmatter 至少包含 `name`(小写字母/数字/连字符)与 `description`(何时使用、具体描述)。
2. 技能主体保持精简,复杂细节放入 `<name>/references/`。
3. 更新本文件技能清单与对应分类 README。
4. 规范细节见 [Agent Skills 规范](https://agentskills.io/specification) 与 pi 的 `docs/skills.md`。

## 如何使用技能

```text
/skill:<name>            # 加载并执行技能
/skill:<name> 参数       # 附带参数
```
