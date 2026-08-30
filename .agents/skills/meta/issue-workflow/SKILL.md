---
name: issue-workflow
description: 按照 Seeker Share 的 Issue 驱动流程处理 GitHub Issue 反馈(分析→修复→验证→提交),适用于处理 Bug、兼容性问题和功能建议。当用户提交或转述项目 Issue 时使用。
---

# Issue 驱动工作流

## 流程

1. **分类**:确定是 Bug / 功能建议 / 兼容性问题 / 文档问题。
2. **分析**:复现或定位问题,评估影响面与涉及模块。
3. **修复**:遵循 [AGENTS.md](../../../../AGENTS.md) 中的工程约定与目录结构。
4. **验证**:执行质量门槛:
   ```bash
   ./mvnw test
   node --check src/main/resources/static/js/tools.js
   node --check src/main/resources/static/js/hanzi-data.js
   ```
   涉及前端时用无头浏览器对 `#/tools/<id>` 冒烟验证。
5. **提交**:Conventional Commits(`fix:`/`feat:`/`docs:` 等),信息说明改动与验证结果,推送 `origin/main`。

## 提交 Issue 建议信息

- 问题现象与复现步骤
- 操作系统 / Java 版本 / 浏览器
- 日志或截图
- 期望的正确行为

## 参考

- 质量门槛详见 [AGENTS.md](../../../../AGENTS.md)
- 人类可读说明见 [docs/AI_CODING.md](../../../../docs/AI_CODING.md)
