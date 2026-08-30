---
name: api-endpoint
description: 在 Seeker Share 后端新增或修改一个受权限保护的 REST API,含 Controller/Service/权限/测试。当需要扩展 /api/v1 接口或调整后端逻辑时使用。
---

# 新增/修改后端 API

## 相关目录

- 控制器:`src/main/java/com/seeker/share/web/`
- 业务与模型:`src/main/java/com/seeker/share/share/`、`security/`
- 通用响应:`src/main/java/com/seeker/share/common/`(ApiResponse / GlobalExceptionHandler)
- 测试:`src/test/java/com/seeker/share/`

## 步骤

1. **控制器薄**:Controller 只做参数绑定与响应包装,业务放入 Service。
2. **权限**:按 `security/PermissionCode.java` 使用对应权限码,所有写操作必须后端强制校验。
3. **异常**:统一抛出业务异常,由 `GlobalExceptionHandler` 转成中文提示的 `ApiResponse`。
4. **持久化**:实体走 Spring Data JPA,字段校验用 Jakarta Validation。
5. **测试**:在 `src/test/java` 增加对应 `*Tests.java`,覆盖正常/鉴权失败/业务异常路径。
6. **验证**:
   ```bash
   ./mvnw test
   ./mvnw spring-boot:run
   ```

## 约束

- 遵循 `common / security / share / web` 分层,不越层
- API 响应统一使用 `ApiResponse` 结构
- 中文错误文案可直接暴露给前端
