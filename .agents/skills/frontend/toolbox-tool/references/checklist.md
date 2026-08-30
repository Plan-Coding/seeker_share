# 工具箱工具 · 提交前检查清单

完成新增/修改工具箱工具后,逐项确认:

- [ ] `node --check src/main/resources/static/js/tools.js` 通过
- [ ] 工具路由 `#/tools/<id>` 可直达,无 JS 报错
- [ ] 界面交互(输入 / 切换 / 点击)正常
- [ ] 未从外部网络/CDN 加载依赖(第三方库已下载到 `static/vendor/` 本地引入并保留许可证)
- [ ] 数据不出本机(纯前端计算)
- [ ] 若涉及汉字数据,未手改 `hanzi-data.js` 内容
- [ ] `README.md` 工具表与数量已同步
