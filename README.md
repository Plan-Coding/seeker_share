# Seeker Share

一个无数据库依赖的局域网消息与文件共享应用。

## 功能

- 在同一局域网内共享文字、链接和代码
- 一键复制单条消息
- 上传、下载最大 100MB 的文件
- 使用 SSE 在所有设备间实时同步共享记录
- 一键清空全部消息和已上传文件
- 支持搜索、类型筛选和删除单条记录
- 支持拖拽、多文件上传与实时进度
- 默认 24 小时自动销毁，限制总共享空间为 1GB
- 可通过环境变量为删除操作设置管理员口令
- 不使用数据库，消息保存在内存，文件保存在本机临时目录

## 技术栈

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Thymeleaf
- Jakarta Validation
- Spring Boot Actuator
- Maven Wrapper

## 本地运行

项目自带 Maven Wrapper，无需预先安装 Maven：

```bash
./mvnw spring-boot:run
```

启动后访问：

- 首页：<http://localhost:8080/>
- 共享 API：<http://localhost:8080/api/v1/shares>
- 健康检查：<http://localhost:8080/actuator/health>

## 常用命令

```bash
./mvnw test
./mvnw clean package
java -jar target/seeker-share-0.0.1-SNAPSHOT.jar
```

可通过环境变量修改端口：

```bash
SERVER_PORT=9090 ./mvnw spring-boot:run
```

文件默认存放在系统临时目录下的 `seeker-share/uploads`，可通过环境变量调整：

```bash
SHARE_STORAGE=/data/seeker-share ./mvnw spring-boot:run
```

生产环境建议设置删除口令，并可调整过期时间与容量：

```bash
ADMIN_TOKEN=your-secret \
EXPIRATION_HOURS=12 \
MAX_STORAGE_BYTES=2147483648 \
mvn spring-boot:run
```

同一局域网设备使用运行主机的 IP 地址访问，例如：

```text
http://192.168.1.10:8080
```

## 目录结构

```text
src/main/java/com/seeker/share/
├── common/       # 通用响应等基础类型
└── web/          # 页面与 REST 控制器
src/main/resources/
├── static/       # CSS、JavaScript、图片等静态资源
└── templates/    # Thymeleaf 页面模板
```
