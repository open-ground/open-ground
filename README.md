# Open Ground

轻量级 Java 后端基础工具包 — 统一响应、加解密工具、操作日志框架、对象存储、序列号生成。

[![Maven Central](https://img.shields.io/maven-central/v/io.github.open-ground/open-ground)](https://central.sonatype.com/artifact/io.github.open-ground/open-ground)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![JDK](https://img.shields.io/badge/JDK-17+-green)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.1-green)

---

## 模块概览

```
open-ground (pom)
├── open-ground-dependency (pom)      — BOM，统一依赖版本管理（含 Spring Boot / Spring Cloud / Spring Cloud Alibaba）
├── open-ground-base (jar)            — 核心 DTO、异常、工具类
├── open-ground-common (pom)          — 功能模块聚合
│   ├── open-ground-common-api (jar)           — SPI 接口、注解、枚举、DTO（零 Spring 依赖）
│   ├── open-ground-common-core (jar)          — AOP 切面、请求过滤器、自动配置、Excel 导入导出
│   └── open-ground-common-springcloud (jar)   — Feign 远程 SPI 实现（微服务模式）
├── open-ground-security (pom)        — 认证安全模块聚合
│   ├── open-ground-security-api (jar)         — 认证 SPI 接口定义（零 Spring 依赖）
│   ├── open-ground-security-core (jar)        — Token 管理、会话存储、API Key、鉴权过滤器
│   └── open-ground-security-springboot-starter (jar) — 自动配置 Starter
├── open-ground-starter (pom)         — 分层 Starter 聚合（按需引入）
│   ├── open-ground-starter-base (pom)         — 基础 Starter：Web + MyBatis + Redis + 工具库
│   ├── open-ground-starter-common (pom)       — 公共组件 Starter：+ 操作日志 / OSS / EasyExcel / JSqlParser
│   ├── open-ground-starter-security (pom)     — 安全 Starter：+ Token / ApiKey / 会话管理
│   ├── open-ground-starter-springcloud (pom)  — 微服务 Starter：+ Nacos / Feign / LoadBalancer
│   └── open-ground-starter-all (pom)          — 全量 Starter：一键引入所有模块
```

### open-ground-base

基础模块，无 Spring Boot 自动配置依赖，包含：

| 类别 | 内容 |
|------|------|
| **DTO** | `CommonResult<T>`（统一响应）、`PaginatedResult<T>`（分页响应）、`Tree<T>`（树节点模型）、`Error`（错误信息） |
| **异常** | `CommonException`（链式调用，含错误码） |
| **常量** | `ErrorCode`（错误码定义：0000 成功、0400-0499 认证授权、1000-1006 系统错误、9999 校验失败）、`PageConstant`（默认分页参数） |
| **加密工具** | `AESUtil`（AES/CBC/PKCS5Padding）、`RSAUtil`（RSA/ECB/PKCS1Padding 2048位）、`SM4Utils`（国密 SM4/CBC/PKCS5）、`SignUtil`（签名验签） |
| **工具类** | `DateUtils`（日期计算与范围生成）、`IpUtils`（客户端 IP 提取）、`JsonUtil`（Fastjson 封装）、`MapUtil`（Map 安全取值）、`AmtUtil`（金额计算）、`ListUtils`（列表排序聚合）、`StringUtils`（字符串处理）、`FileUtil`（文件上传）、`PageUtil`（分页计算）、`SerializeUtil`（序列化）、`SpringUtil`（静态 Bean 获取）、`RequestUtil`（请求上下文 ThreadLocal）、`FormatHashMap`（点号键 HashMap）、`CommonUtil`（通用工具） |

### open-ground-common-api

SPI 接口定义层，**零 Spring 依赖**，可被非 Spring 项目引用：

- **操作日志 SPI** — `@OptLog` 注解、`LogSender` 接口、`SysOptLog` DTO、`OptType`/`OptStatus` 枚举、`OptLogEvent` 事件
- **Excel 导入导出 SPI** — `@ExcelTemplate` 注解、`@ExcelField` 注解、`DictTranslator` 接口、`ExcelQueryProvider` 接口、`ExcelDataValidator` 接口、`ImportResult`/`ImportRowError` DTO
- **序列号 SPI** — `SequenceProvider` 接口、`IdGenerator`（53 位雪花算法）、`KeyInfoDomain` 序列元数据
- **条件注解** — `@ConditionalOnAuth`（集成模式）、`@ConditionalOnService`（微服务模式）

### open-ground-common-core

Spring Boot 自动配置模块，提供 SPI 接口的默认实现：

- **请求过滤器** — `CommonRequestFilter`：Token 校验、AES/SM4 解密、签名验签、SQL 注入拦截、URL 非法字符检查。通过 `ground.security.request-filter.*` 配置
- **操作日志** — `OptLogAspect` AOP 切面 + `JdbcLogSender`（JDBC 持久化），通过 `@EnableOptLog` 启用
- **Excel 导入导出** — `GenericExcelController` 内置通用 REST 入口、`ExcelService` 核心服务、`TableQueryProvider`（MyBatis-Plus 自动查询/插入）、`ImportAnalysisListener`（EasyExcel 导入监听器），通过 `@ExcelTemplate` + `@ExcelField` 注解驱动，零代码开箱即用
- **序列号生成** — `JdbcSequenceProvider`（基于数据库表 `SYS_AUTO_PMKEY`）、`KeyGenerator`（缓存式批量生成）、`Snowflake`（16 位雪花算法）
- **对象存储** — `OssClient` 接口 + `S3OssClient`（AWS S3 SDK 实现），通过 `ground.oss.*` 配置
- **多数据源管理** — 统一的多数据源组件，支持两种数据源来源：① `ground.dblist` 配置式（静态）；② `sys_datasource` 表式（动态，AES 加密存储密码）。基于 Druid 连接池，通过 `DataSourceProvider` SPI 扩展
  - `DynamicDataSourceManager` — Druid 连接池管理器，按 `dsName` 路由，支持 `getDbType()`/`getDefaultDbType()` 数据库类型推断
  - `DynamicJdbcTemplate` — 动态 JDBC 模板，支持参数化查询（NamedParameterJdbcTemplate）、原始 SQL 执行（`execSql`，CLOB/NCLOB 处理）、DDL 执行、存储过程调用、分页 SQL 生成、SQL 注入检测
  - `DbDialect` SPI — 数据库方言适配器（MySQL/Oracle/PostgreSQL/DM/Tbase），提供分页 SQL、表列表 SQL、表字段 SQL、列值格式化能力，替代各项目 `IDataSourceService`
  - `DataSourceProvider` SPI — 数据源提供者接口，`ConfigDataSourceProvider`（dblist，order=10）和 `SysDatasourceProvider`（sys_datasource 表，order=20）自动注册，高优先级覆盖低优先级
  - `SysDatasourceController` — 系统数据源管理 REST API（CRUD + 测试连接 + 表结构查询）
  - `DbTypeDetector` — 统一数据库类型推断（driverClassName/URL/dbType 三级推断）
  - `SqlUtils` — 分页 SQL 生成 + SQL 注入检测

### open-ground-common-springcloud

微服务模式下的 Feign 远程实现，通过 `ground.mode=service` 激活：

- `FeignTokenCheckService` — 远程调用认证服务 Token 校验
- `FeignSequenceProvider` — 远程获取序列号
- `FeignLogSender` — 远程保存操作日志

### open-ground-starter-base

基础 Starter，适用于只需要核心 DTO + Web + 数据库的项目：

- Open Ground 自身模块：`open-ground-base`
- Spring Boot：Web（Undertow）、AOP、Data Redis、Configuration Processor
- 数据库：MyBatis、Druid、MySQL、PageHelper
- API 文档：SpringDoc OpenAPI
- 安全/Token：JJWT、Spring Security Crypto
- 工具：Lombok、Commons Lang3、Guava
- 日志：Logback Classic

### open-ground-starter-common

公共组件 Starter，在 base 基础上增加完整功能模块：

- 继承：`open-ground-starter-base` 全部依赖
- Open Ground 公共组件：`open-ground-common-api` + `open-ground-common-core`
- JDBC（JdbcSequenceProvider 需要）
- 对象存储：AWS S3 SDK
- 数据处理：EasyExcel
- 配置加密：Jasypt
- HTTP Client：HttpClient5
- 工具库：Dom4j、UserAgentUtils、JSqlParser

### open-ground-starter-security

安全 Starter，在 common 基础上增加完整认证能力：

- 继承：`open-ground-starter-common` 全部依赖
- Open Ground 安全模块：`open-ground-security-api` + `open-ground-security-core` + `open-ground-security-springboot-starter`

### open-ground-starter-springcloud

微服务 Starter，在 security 基础上增加微服务基础设施：

- 继承：`open-ground-starter-security` 全部依赖
- Open Ground 微服务模块：`open-ground-common-springcloud`
- 服务注册/配置：Nacos Discovery + Nacos Config
- 服务调用：OpenFeign + Feign OkHttp
- 负载均衡：Spring Cloud LoadBalancer
- 引导上下文：Spring Cloud Bootstrap

### open-ground-starter-all

全量 Starter，一键引入所有模块，兼容旧版 `open-ground-starter`：

- 继承：`open-ground-starter-springcloud` 全部依赖
- 等同于引入全部 Open Ground 功能

### open-ground-dependency

纯 BOM 模块（无任何依赖声明），提供 `<dependencyManagement>` 供其他项目 `import` 使用。统一管理以下版本：

| 类别 | 主要依赖 |
|------|----------|
| **Spring Boot** | spring-boot-dependencies 3.4.4 |
| **Spring Cloud** | spring-cloud-dependencies 2024.0.1 |
| **Spring Cloud Alibaba** | spring-cloud-alibaba-dependencies 2023.0.3.2 |
| **数据库** | MyBatis 3.0.4、Druid 1.2.24、MySQL Connector 8.2.0、PageHelper 2.1.0 |
| **安全** | JJWT 0.11.5、Jasypt 3.0.5 |
| **API 文档** | SpringDoc OpenAPI 2.8.4 |
| **工具** | Lombok 1.18.38、Fastjson 2.0.57、Hutool 5.8.38、Guava 33.4.6-jre、Commons Lang3 3.17.0 |
| **存储** | AWS S3 1.12.362 |
| **其他** | EasyExcel 3.1.1、HttpClient5 5.4.2、Dom4j 2.1.4、JSqlParser 4.7、Logback 1.5.18 |

### open-ground-security

认证安全模块，提供完整的用户认证与会话管理能力：

| 子模块 | 说明 |
|--------|------|
| `open-ground-security-api` | SPI 接口定义层：`UserDetails`（用户详情）、`UserDetailsService`（用户查询服务）、`TokenStore`（会话存储接口）、`ApiKeyService`（API Key 服务）、`TokenManager`（Token 管理器）、`SecurityContextHolder`（安全上下文） |
| `open-ground-security-core` | 核心实现：`DbTokenStore`（数据库会话存储）、`RedisTokenStore`（Redis 会话存储）、`ApiKeyServiceImpl`（API Key 实现）、`AuthTokenManagerFilter`（Token 鉴权过滤器）、`TokenExtractor`（Token 提取 SPI）、`SessionMapper`/`ApiKeyMapper`（MyBatis 映射） |
| `open-ground-security-springboot-starter` | Spring Boot 自动配置，通过 `@EnableConfigurationProperties` 加载 `AuthProperties` 和 `TokenFilterProperties` |

**核心能力：**

- **用户认证** — 基于 `UserDetailsService` SPI 的用户认证机制，支持用户名、手机号、用户ID 等多种查询方式
- **Token 管理** — 基于 `TokenManager` 的 Token 生成、验证（含滑动过期）、刷新、销毁，支持多设备登录控制
- **会话存储** — 支持数据库（`DbTokenStore`，默认）和 Redis（`RedisTokenStore`）两种存储方式，通过 `ground.security.token-store` 切换
- **API Key 管理** — `sk-` 前缀的 API Key 生成、验证、撤销，支持过期时间和启用/禁用控制
- **鉴权过滤器** — `AuthTokenManagerFilter` 自动拦截请求，提取 Token 并校验，支持白名单路径放行和自定义 `TokenExtractor`
- **安全上下文** — `SecurityContextHolder` 基于 ThreadLocal 的当前用户上下文管理，请求结束后自动清除

---

## 架构设计

项目采用 **SPI + 双部署模式** 架构：

```
              open-ground-starter（分层聚合，按需引入）
                         │
     ┌───────────┬───────┴───────┬──────────────────┐
     │           │               │                  │
  starter-base  starter-common  starter-security  starter-springcloud  starter-all
     │           │               │                  │                    │
     │     ┌─────┴─────┐   ┌────┴────┐        ┌────┴────┐          (全量)
     │  common-api  core  security  security  springcloud
     │  (SPI 定义) (实现)  -api     -core      (Feign)
     │              │    (SPI)    (Token)        │
  base (DTO/工具)  AOP/过滤器  用户认证   ApiKey     Nacos/Feign/LoadBalancer
                    JDBC 实现   会话存储   Session
```

### 两种部署模式

通过 `ground.mode` 配置切换：

| 模式 | 值 | 说明 |
|------|----|------|
| **集成模式** | `auth`（默认） | 直接通过 JDBC 操作数据库，适用于单体应用 |
| **微服务模式** | `service` | 通过 Feign 远程调用认证服务，适用于微服务架构 |

---

## 快速开始

### 引入依赖

Starter 按功能层级划分，按需引入：

```xml
<!-- ① 基础 Starter：Web + MyBatis + Redis + 工具库（最小依赖） -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-base</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>

<!-- ② 公共组件 Starter：+ 操作日志 / OSS / EasyExcel / JSqlParser -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-common</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>

<!-- ③ 安全 Starter：+ Token / ApiKey / 会话管理 -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-security</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>

<!-- ④ 微服务 Starter：+ Nacos / Feign / LoadBalancer -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-springcloud</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>

<!-- ⑤ 全量 Starter：一键引入所有模块 -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-all</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>
```

也可以按需单独引入模块：

```xml
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-base</artifactId>
    <version>${open-ground.version}</version>
</dependency>
```

### 统一响应

```java
// 成功
return CommonResult.success(data);
return CommonResult.success("操作成功", data);

// 分页
return PaginatedResult.success(list, total, page, perPage);

// 失败（通过异常）
throw new CommonException().setCode(ErrorCode.USER_NOT_FOUND_ERROR).setMsg("用户不存在");
```

### 启用操作日志

```java
@SpringBootApplication
@EnableOptLog  // 启用操作日志 AOP
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```java
@RestController
public class UserController {

    @OptLog(value = "创建用户", type = OptType.INSERT)
    @PostMapping("/user")
    public CommonResult<User> create(@RequestBody UserDTO dto) {
        // ...
    }
}
```

### 启用请求过滤器

```yaml
ground:
  mode: auth   # 集成模式（默认）
  security:
    request-filter:
      enabled: true               # 启用过滤器
      token-check-enabled: true   # 启用 Token 校验
      decrypt-enabled: true       # 启用请求解密
      algorithm: AES              # 解密算法：AES / SM4
      encrypt-key: ${ENCRYPT_KEY}
      encrypt-iv: ${ENCRYPT_IV}
      sign-expire: 300            # 签名过期时间（秒）
```

实现 `TokenCheckService` SPI 接口并注册为 Bean 即可自定义 Token 校验逻辑：

```java
@Component
public class MyTokenCheckService implements TokenCheckService {
    @Override
    public void check(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new CommonException().setCode("0401").setMsg("无效 Token");
        }
        // 自定义校验逻辑...
    }
}
```

### 使用序列号生成器

```java
@Component
public class MyService {
    @Autowired
    private KeyGenerator keyGenerator;

    public void createOrder() {
        // 格式化序列：前缀 + 日期 + 流水号，如 ORD202501010001
        String orderNo = keyGenerator.businessKey("ORD", "ORDER_SEQ");
        // 雪花算法 ID
        Long id = keyGenerator.internalKey();
        // 纯数字序列
        Long nextKey = keyGenerator.nextKey("ORDER_SEQ");
    }
}
```

### 使用对象存储

```yaml
ground:
  oss:
    enable: true
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    region: cn-hangzhou
    path-style-access: false
    domain: https://your-bucket.oss-cn-hangzhou.aliyuncs.com
    access-key: ${OSS_ACCESS_KEY}
    access-secret: ${OSS_ACCESS_SECRET}
    bucket-name: your-bucket
```

```java
@Component
public class MyService {
    @Autowired
    private OssClient ossClient;

    public void upload(byte[] data, String key) {
        ossClient.putObject(data, key);
        String url = ossClient.getObjectURL(key, 3600); // 1小时有效期
    }
}
```

### 使用 Excel 导入导出组件

> **零代码模式**：只需一个 VO 类 + 注解，自动注册 REST 端点，无需编写 Controller、Service、Mapper。

#### ① 定义 VO 类

```java
@ExcelTemplate(tableName = "sys_user")  // 自动 SELECT * FROM sys_user
public class UserExcelVO {

    @ExcelField(headerName = "用户名", order = 1)
    private String username;

    @ExcelField(headerName = "昵称", order = 2)
    private String nickname;

    @ExcelField(headerName = "性别", order = 3, dictType = "gender")
    private String gender;

    @ExcelField(headerName = "邮箱", order = 4, required = true)
    private String email;

    @ExcelField(headerName = "创建时间", order = 5, dateFormat = "yyyy-MM-dd")
    private Date createTime;
}
```

#### ② 直接调用内置 REST 接口

组件自动扫描所有 `@ExcelTemplate` 类并注册 URL，无需编写任何 Controller：

| 方法 | URL | 说明 |
|------|-----|------|
| `POST` | `/ground/excel/{entityName}/export` | 导出 Excel（JSON body 传入查询条件） |
| `POST` | `/ground/excel/{entityName}/import` | 导入 Excel，返回 JSON 结果（multipart file） |
| `POST` | `/ground/excel/{entityName}/import?errorAsFile=true` | 导入 Excel，有错误时返回带错误原因的文件 |
| `GET` | `/ground/excel/{entityName}/template` | 下载导入模板（仅表头） |
| `GET` | `/ground/excel/entities` | 查看已注册实体列表 |

`entityName` 自动生成规则：
- `@ExcelTemplate(name = "user")` → 使用 `user`
- `UserExcelVO` → `user-excel`（类名去掉 ExcelVO/VO 后缀，转 kebab-case）

**参数查询示例（TABLE 模式）：**

零代码模式下，通过 JSON body 传入查询参数，自动转为 SQL WHERE 条件（等值匹配）：

```bash
# 导出所有用户（不传 body）
curl -X POST http://localhost:8080/ground/excel/user/export

# 只导出用户名为 zhangsan 的记录
curl -X POST http://localhost:8080/ground/excel/user/export \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan"}'

# 多条件组合：部门 = dev + 状态 = 1
curl -X POST http://localhost:8080/ground/excel/user/export \
  -H "Content-Type: application/json" \
  -d '{"dept_id":"dev","status":"1"}'
```

生成的 SQL：`SELECT * FROM sys_user WHERE username = ? AND status = ?`（参数化防注入）

> **提示**：目前仅支持等值（=）查询。如需复杂条件（LIKE、范围、排序等），请使用 CUSTOM 模式自定义 `ExcelQueryProvider`。

#### ③ 自定义字典翻译

默认字典翻译器不翻译，返回原值。实现 `DictTranslator` 接口并声明为 Spring Bean 即可覆盖：

```java
@Component
public class MyDictTranslator implements DictTranslator {
    @Override
    public String translate(String dictType, String value) {
        if ("gender".equals(dictType)) {
            return "0".equals(value) ? "男" : "女";
        }
        if ("status".equals(dictType)) {
            return "1".equals(value) ? "启用" : "禁用";
        }
        return value;
    }
}
```

导入时同样支持反向翻译（label → code），由同一个 `DictTranslator` 处理。

#### ④ 自定义查询/落库逻辑

当 `@ExcelTemplate(queryType = CUSTOM)` 时，框架调用自定义 `ExcelQueryProvider`：

```java
@Component
public class UserExcelProvider implements ExcelQueryProvider {
    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        return userService.listByCustomCondition(params);
    }

    @Override
    public void handleImport(List<?> data, Map<String, Object> params) {
        for (Object row : data) {
            userService.saveOrUpdate((UserExcelVO) row);
        }
    }
}
```

```java
@ExcelTemplate(queryProvider = UserExcelProvider.class, queryType = QueryType.CUSTOM)
public class UserExcelVO { ... }
```

#### ⑤ 数据校验

**必填校验**：`@ExcelField(required = true)`

**自定义校验器**：实现 `ExcelDataValidator` 接口：

```java
public class PhoneValidator implements ExcelDataValidator {
    @Override
    public String validate(Object value, String headerName) {
        String phone = (String) value;
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) {
            return "手机号格式不正确";
        }
        return null;  // 校验通过
    }
}
```

```java
@ExcelField(headerName = "手机号", required = true, validator = PhoneValidator.class)
private String phone;
```

#### ⑥ 导入错误文件下载

当导入数据存在校验失败时，可通过 `?errorAsFile=true` 参数让接口直接返回一个带错误原因的 Excel 文件：

```bash
curl -X POST http://localhost:8080/ground/excel/user/import?errorAsFile=true \
  -F "file=@users.xlsx"
```

返回的 Excel 文件结构：
- 包含原始 VO 中定义的所有列
- 末尾自动增加 **"错误原因"** 列
- **仅包含校验失败的行**，每行显示其原始数据 + 错误描述
- 全部成功时仍返回 JSON 格式的 `ImportResult`

#### ⑦ 配置项

```yaml
ground:
  excel:
    enabled: true                    # 是否启用 Excel 组件（默认 true）
    controller-enabled: true         # 是否启用通用 Controller（默认 true）
    scan-packages:                   # 额外扫描包路径（可选）
      - com.example.business
```

### 加密工具使用

```java
// AES 加解密
String encrypted = AESUtil.encrypt("Hello", key, iv);
String decrypted = AESUtil.decrypt(encrypted, key, iv);

// RSA 加解密
KeyPair keyPair = RSAUtil.getKeys();
String encrypted = RSAUtil.encrypt("Hello", keyPair.getPublic());
String decrypted = RSAUtil.decrypt(encrypted, keyPair.getPrivate());

// 国密 SM4 加解密
String encrypted = SM4Utils.encrypt("Hello", key, iv);
String decrypted = SM4Utils.decrypt(encrypted, key, iv);
```

### 使用认证安全模块

引入依赖：

```xml
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-security-springboot-starter</artifactId>
    <version>${open-ground.version}</version>
</dependency>
```

#### 实现 UserDetailsService

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        return DefaultUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .realName(user.getRealName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .roleIds(user.getRoleIds())
                .status(user.getStatus())
                .enabled(user.getStatus() == 1)
                .accountNonLocked(user.getStatus() == 1)
                .createTime(user.getCreateTime())
                .build();
    }
}
```

#### 用户登录（生成 Token）

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private TokenManager tokenManager;
    @Autowired
    private UserDetailsService userDetailsService;

    @PostMapping("/login")
    public CommonResult<String> login(@RequestParam String username,
                                      @RequestParam String password,
                                      HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        // 验证密码（业务自行实现）
        // ...

        // 设置客户端 IP
        DefaultUserDetails details = (DefaultUserDetails) userDetails;
        details.setClientIp(IpUtils.getIpAddr(request));

        // 生成 Token
        Map<String, String> params = new HashMap<>();
        String token = tokenManager.generateToken(details, "password", params);
        return CommonResult.success(token);
    }
}
```

#### 获取当前用户

```java
@GetMapping("/user/info")
public CommonResult<UserInfo> getUserInfo() {
    UserDetails currentUser = SecurityContextHolder.getCurrentUser();
    if (currentUser == null) {
        throw new CommonException(ErrorCode.NO_LOGIN, "未登录");
    }
    // 转换为业务 DTO...
    return CommonResult.success(userInfo);
}
```

#### 配置 Token 鉴权过滤器

```yaml
ground:
  security:
    token-store: db                      # 会话存储方式：db（默认）/ redis
    token-timeout: 1440                  # Token 超时时间（分钟），默认 1440（24小时）
    multi-login: true                    # 是否允许多设备登录
    token-filter:
      enabled: true                      # 启用 Token 鉴权过滤器
      white-list:                        # 白名单路径（无需 Token 校验）
        - /auth/login
        - /auth/register
        - /swagger-ui/**
        - /v3/api-docs/**
      order: -100                        # 过滤器顺序

---

## 配置参考

### 完整配置项

```yaml
ground:
  mode: auth                                    # 部署模式：auth（集成）/ service（微服务）

  security:
    request-filter:
      enabled: false                            # 是否启用请求过滤器
      order: -100                               # 过滤器顺序
      url-regular-enabled: true                 # 是否启用 URL 正则校验
      decrypt-enabled: false                    # 是否启用请求解密
      algorithm: AES                            # 解密算法：AES / SM4
      encrypt-key: ''                           # 加密密钥
      encrypt-iv: ''                            # 加密向量
      sign-expire: 300                          # 签名过期时间（秒）
      token-check-enabled: false                # 是否启用 Token 校验

  log:
    enabled: true                               # 是否启用操作日志
    log-length: 2000                            # 日志参数截断长度

  security:
    token-store: db                             # 会话存储方式：db / redis
    token-timeout: 1440                         # Token 超时时间（分钟）
    multi-login: true                           # 是否允许多设备登录
    token-filter:
      enabled: true                             # 是否启用 Token 鉴权过滤器
      order: -100                               # 过滤器顺序
      white-list:                               # 白名单路径（Ant 路径模式）
        - /swagger-ui/**
        - /v3/api-docs/**
        - /error
        - /favicon.ico
      url-patterns:                             # 拦截路径
        - /*
    api-key:
      enabled: true                             # 是否启用 API Key
      expire-days: 30                           # API Key 默认过期天数

  oss:
    enable: false                               # 是否启用对象存储
    endpoint: ''                                # OSS 端点
    region: ''                                  # 区域
    path-style-access: false                    # 是否使用路径风格
    domain: ''                                  # 自定义域名
    access-key: ''                              # 访问密钥
    access-secret: ''                           # 访问密钥密码
    bucket-name: ''                             # 默认桶名
    max-connections: 100                        # 最大连接数
```

---

## 要求

- JDK 17+
- Spring Boot 3.x
- Maven 3.6+

## 许可

Apache License 2.0

Copyright 2025 Open Ground
