# Open Ground Security

Open Ground Security 是 open-ground 项目的认证安全模块，提供统一的认证安全能力，基于 **SPI 接口 + 双存储实现** 架构，支持集成部署和微服务部署两种模式。

---

## 模块结构

```
open-ground-security (pom)
├── open-ground-security-api (jar)                    — SPI 接口定义层
├── open-ground-security-core (jar)                   — 核心实现层
└── open-ground-security-springboot-starter (jar)     — Spring Boot 自动配置
```

### open-ground-security-api

SPI 接口定义层，依赖 `open-ground-base`（CommonResult、CommonException 等），提供认证安全的核心抽象：

| 类/接口 | 说明 |
|---------|------|
| `UserDetails` | 用户详情接口，对标 Spring Security 的 UserDetails，包含 id、username、password、realName、mobile、email、avatar、roleIds、roleNames、authorities、orgId、orgName、corpId、corpName、createTime、lastLoginTime、extData、clientIp 以及账户状态方法（isAccountNonExpired、isAccountNonLocked、isCredentialsNonExpired、isEnabled） |
| `DefaultUserDetails` | UserDetails 的默认实现（Lombok `@Builder`），业务模块可直接使用 |
| `UserDetailsService` | 用户查询 SPI，核心方法 `loadUserByUsername(String)`，可选实现 `loadUserByMobile(String)` 和 `loadUserById(Long)` |
| `UserNotFoundException` | 用户未找到异常，继承 `CommonException` |
| `TokenStore` | Token 存储 SPI，定义 `findByToken`、`findById`、`findByUsername`、`save`、`update`、`deleteById`、`deleteByUsername`、`cleanExpired` 方法 |
| `TokenManager` | Token 管理器（`@Component`），负责 Token 生成、验证（滑动过期）、刷新、销毁，以及会话上下文管理（ThreadLocal） |
| `SessionEntity` | 会话实体，映射 `sys_session` 表，包含 id、sessionData（JSON 格式的 UserDetails）、token、username、grantType、createTime、lastAccessTime、expireTime（yyyyMMddHHmmss 格式）、host |
| `ApiKeyService` | API Key 服务 SPI，定义 `generateKey`、`validateKey`、`revokeKey`、`getUserKeyList` 方法 |
| `ApiKeyDO` | API Key 实体，映射 `sys_api_key` 表，包含 id、userId、username、apiKey（`@JsonProperty(WRITE_ONLY)`）、name、status、expireTime、lastUsedTime 及审计字段 |
| `SecurityContextHolder` | 安全上下文持有者（ThreadLocal），提供 `setCurrentUser`、`getCurrentUser`、`getCurrentUsername`、`getCurrentUserId`、`clear` 静态方法 |
| `AuthProperties` | 认证配置属性（`ground.security.*`），包含 tokenStore、tokenTimeout、multiLogin、refreshBlacklist、apiKey 子配置 |

### open-ground-security-core

核心实现层，依赖 `open-ground-security-api`、MyBatis、Spring Boot Web、Redis（可选）：

| 类 | 说明 |
|----|------|
| `DbTokenStore` | 基于数据库的 Token 存储（`@ConditionalOnProperty(name="ground.security.token-store", havingValue="db", matchIfMissing=true)`），通过 `SessionMapper` 操作 `sys_session` 表 |
| `RedisTokenStore` | 基于 Redis 的 Token 存储（`@ConditionalOnProperty(name="ground.security.token-store", havingValue="redis")`），使用 RedisTemplate 操作，Redis Key 前缀：`security:session:`、`security:token:`、`security:user:sessions:` |
| `ApiKeyServiceImpl` | API Key 服务实现，`sk-` + UUID 生成 Key，支持状态校验、过期校验、最后使用时间更新 |
| `AuthTokenManagerFilter` | Token 鉴权过滤器（实现 `Filter`），拦截请求提取 Token 并校验，支持白名单路径放行和 API Key 认证（`sk-` 前缀） |
| `TokenExtractor` | Token 提取 SPI（`@FunctionalInterface`），从 HTTP 请求中提取 Token 字符串 |
| `DefaultTokenExtractor` | 默认 Token 提取器，从 `Authorization: Bearer xxx` 请求头中提取 |
| `SessionMapper` | 会话 MyBatis Mapper，操作 `sys_session` 表 |
| `ApiKeyMapper` | API Key MyBatis Mapper，操作 `sys_api_key` 表 |
| `SecurityAutoConfiguration` | 安全自动配置，`@ComponentScan` + `@MapperScan` 扫描 security 包 |
| `TokenFilterAutoConfiguration` | Token 过滤器自动配置，注册 `AuthTokenManagerFilter`（`@ConditionalOnWebApplication` + `@ConditionalOnProperty`） |
| `TokenFilterProperties` | 过滤器配置属性（`ground.security.token-filter.*`），包含 enabled、whiteList、order、urlPatterns |

### open-ground-security-springboot-starter

Spring Boot Starter 模块，聚合 `open-ground-security-core`，通过 `AutoConfiguration.imports` 注册自动配置类：

```
io.github.openground.common.security.config.SecurityAutoConfiguration
io.github.openground.common.security.config.TokenFilterAutoConfiguration
```

---

## 数据库表结构

### sys_session（会话表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) PK | 会话ID |
| session_data | TEXT | 会话数据（JSON 格式的 UserDetails） |
| token | VARCHAR(255) NOT NULL | Token |
| username | VARCHAR(100) NOT NULL | 用户名 |
| grant_type | VARCHAR(50) | 授权类型（password/sso/api_key 等） |
| create_time | DATETIME NOT NULL | 创建时间 |
| last_access_time | DATETIME | 最后访问时间 |
| expire_time | DATETIME NOT NULL | 过期时间 |
| host | VARCHAR(100) | 客户端 IP |

索引：`idx_username`、`idx_token`、`idx_expire_time`

### sys_api_key（API Key 表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) PK | Key ID |
| user_id | VARCHAR(64) NOT NULL | 用户ID |
| username | VARCHAR(100) NOT NULL | 用户名 |
| api_key | VARCHAR(255) NOT NULL | API Key 值 |
| name | VARCHAR(100) | Key 名称 |
| status | TINYINT DEFAULT 1 | 状态：0-禁用，1-启用 |
| expire_time | DATETIME | 过期时间 |
| last_used_time | DATETIME | 最后使用时间 |
| create_time | DATETIME NOT NULL | 创建时间 |
| update_time | DATETIME | 更新时间 |
| create_by | VARCHAR(100) | 创建人 |
| update_by | VARCHAR(100) | 修改人 |

唯一索引：`uk_api_key`，索引：`idx_username`、`idx_user_id`

---

## 配置参考

### 完整配置项

```yaml
ground:
  security:
    # Token 存储方式：db（默认）或 redis
    token-store: db
    # Token 超时时间（分钟），默认 1440（24小时）
    token-timeout: 1440
    # 是否允许多设备登录
    multi-login: true
    # Token 刷新黑名单路径（这些路径不会触发 Token 有效期刷新）
    refresh-blacklist:
      - /auth/uaa/info/list

    # Token 鉴权过滤器配置
    token-filter:
      enabled: true
      order: -100
      white-list:
        - /swagger-ui/**
        - /v3/api-docs/**
        - /error
        - /favicon.ico
      url-patterns:
        - /*

    # API Key 配置
    api-key:
      enabled: true
      expire-days: 30
```

---

## 核心流程

### Token 认证流程

```
请求 → AuthTokenManagerFilter
  ├─ 白名单匹配 → 放行
  └─ Token 提取（DefaultTokenExtractor）
       ├─ sk- 开头 → TokenManager.validateApiKey() → ApiKeyService.validateKey()
       └─ 普通 Token → TokenManager.validateAndRefreshToken()
            ├─ TokenStore.findById() → 不存在 → 返回 401
            ├─ 已过期 → TokenStore.deleteById() → 返回 401
            └─ 有效 → 滑动过期刷新 → 设置 ThreadLocal → 放行
                            ↓
                    请求处理完成 → finally → clearCurrentSession()
```

### 登录流程

```
登录请求 → 业务 Controller
  ├─ UserDetailsService.loadUserByUsername() → 查询用户
  ├─ 密码校验（业务自行实现）
  ├─ TokenManager.generateToken()
  │   ├─ TokenStore.findByUsername() → 检查已有会话
  │   ├─ 多设备登录控制（IP 校验 / 踢下线）
  │   └─ TokenStore.save() / update()
  └─ 返回 Token
```

---

## 快速集成

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-security-springboot-starter</artifactId>
    <version>${open-ground.version}</version>
</dependency>
```

### 2. 执行 DDL

执行 `open-ground-security-core/src/main/resources/db/schema.sql` 创建 `sys_session` 和 `sys_api_key` 表。

### 3. 实现 UserDetailsService

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) {
        // 查询用户信息
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

### 4. 用户登录

```java
@PostMapping("/login")
public CommonResult<String> login(@RequestParam String username,
                                  @RequestParam String password,
                                  HttpServletRequest request) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    // 验证密码...
    ((DefaultUserDetails) userDetails).setClientIp(IpUtils.getIpAddr(request));
    String token = tokenManager.generateToken(userDetails, "password", null);
    return CommonResult.success(token);
}
```

### 5. 获取当前用户

```java
UserDetails currentUser = SecurityContextHolder.getCurrentUser();
```

---

## 二次开发指南

### 自定义 Token 提取策略

实现 `TokenExtractor` 接口并注册为 Bean，覆盖默认的 Header 提取方式：

```java
@Component
public class CookieTokenExtractor implements TokenExtractor {
    @Override
    public String extract(HttpServletRequest request) {
        // 从 Cookie 中提取 Token
        Cookie[] cookies = request.getCookies();
        // ...
    }
}
```

### 自定义 TokenStore

实现 `TokenStore` 接口，支持 MongoDB、JPA 等存储方式：

```java
@Component("mongoTokenStore")
public class MongoTokenStore implements TokenStore {
    // 实现所有接口方法
}
```

### 自定义 UserDetails

实现 `UserDetails` 接口添加自定义字段：

```java
@Data
public class CustomUserDetails implements UserDetails {
    private Long id;
    private String username;
    // ... 其他字段
    private String department;
    private Map<String, Object> extraAttributes;
}
```

---

## API 参考

### TokenManager

| 方法 | 说明 |
|------|------|
| `generateToken(UserDetails, String, Map)` | 生成 Token，支持多设备登录控制和 IP 校验 |
| `generateTokenSso(String)` | SSO 单点登录生成 Token |
| `validateAndRefreshToken(String, String)` | 校验 Token 并滑动刷新有效期 |
| `validateToken(String)` | 校验 Token（不刷新） |
| `invalidateToken(String)` | 注销 Token |
| `invalidateUserTokens(String)` | 销毁用户的所有 Token |
| `validateApiKey(String)` | 校验 API Key |
| `setCurrentSession(SessionEntity)` | 设置当前会话到 ThreadLocal |
| `getCurrentSession()` | 获取当前会话 |
| `getCurrentUser()` | 获取当前用户信息 |
| `clearCurrentSession()` | 清除 ThreadLocal |

### SecurityContextHolder

| 方法 | 说明 |
|------|------|
| `setCurrentUser(UserDetails)` | 设置当前用户 |
| `getCurrentUser()` | 获取当前用户 |
| `getCurrentUsername()` | 获取当前用户名 |
| `getCurrentUserId()` | 获取当前用户ID |
| `clear()` | 清除上下文（防止内存泄漏） |

### ApiKeyService

| 方法 | 说明 |
|------|------|
| `generateKey(String, String, Date)` | 生成 API Key（`sk-` + UUID） |
| `validateKey(String)` | 验证 API Key（格式、状态、过期） |
| `revokeKey(String)` | 撤销 API Key |
| `getUserKeyList(String)` | 获取用户的 API Key 列表 |

---

## 版本历史

- **v0.1.0** (2025-01)
  - 初始版本
  - 支持用户认证、Token 管理、API Key 管理
  - 支持数据库和 Redis 两种会话存储方式
  - 提供 Spring Boot Starter 自动配置
  - Token 鉴权过滤器支持白名单和 API Key 认证
