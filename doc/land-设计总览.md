# open-ground-land 模块设计总览

> **目标读者**：二次开发人员、LLM 辅助开发
> **目的**：无需通读源码即可理解 land 的设计理念、架构分层、内置 Job、多数据源、服务转发与数据交换机制，并能快速上手二次开发。
> **版本基准**：open-ground 1.0.6+（含 1.0.7 dmp 模块）
> **源码路径**：`open-ground/open-ground-land/`

---

## 目录

- [§1 模块总览](#1-模块总览)
- [§2 设计理念](#2-设计理念)
- [§3 调度引擎核心](#3-调度引擎核心)
- [§4 内置 Job 体系](#4-内置-job-体系)
- [§5 多数据源机制](#5-多数据源机制)
- [§6 服务转发（dispatch）](#6-服务转发dispatch)
- [§7 DMP 数据交换](#7-dmp-数据交换)
- [§8 二次开发规范速查](#8-二次开发规范速查)
- [§9 核心库表速查](#9-核心库表速查)
- [§10 LLM 快速上下文](#10-llm-快速上下文)

---

## §1 模块总览

### 一句话定位

**open-ground-land 是基于 open-ground 框架的分布式批处理调度引擎**，提供定时任务调度、条件依赖触发、分布式故障转移、远程任务转发、数据交换（文件↔库、库↔库）等能力，是金融跑批、ETL、日切等批处理场景的核心基础设施。

### 子模块划分

land 采用 `api ← core ← {dispatch, dmp}` 的自底向上分层依赖：

| 子模块 | 职责定位 | 关键内容 |
|--------|---------|---------|
| **open-ground-land-api** | 接口契约层 | `JobEngine` 抽象类、`RemoteTaskExecutor` 接口、`JobOut`/`JobContext`/`StepSegmentExeInfo` 域对象、Task 相关 DTO |
| **open-ground-land-core** | 调度引擎核心 | 7 个调度线程、6 个配置类、6 个 Mapper+XML、内置 Job（11 个）、Entity/常量/工具类 |
| **open-ground-land-dispatch** | 调度中心接入层 | 4 个 Controller、`DbRemoteTaskExecutor`（远程转发）、`DbServiceDiscovery`（DB 服务发现） |
| **open-ground-land-dmp** | 数据管理平台 | `DataExchangeJob` + 3 个执行器（DbToDb/DbToFile/FileToDb）、数据交换配置管理（Mapper 继承 BaseMapper，与 land-core 一致） |

### 依赖拓扑

```
                    open-ground-starter-land (pom 聚合)
                              │
           ┌──────────────────┼──────────────────┐
           ▼                  ▼                  ▼
    open-ground-land-api  open-ground-land-core  open-ground-starter-common
           │                  ▲        ▲            (传递 MyBatis/Redis/Web/
           └──────────────────┘        │             Druid/PageHelper/SpringDoc/JWT)
                              │        │
                   ┌──────────┴────────┴───┐
                   ▼                       ▼
          open-ground-land-dispatch   open-ground-land-dmp
```

- `api` 是最底层契约，无业务实现，不依赖任何 land 子模块
- `core` 依赖 `api` + `open-ground-starter-common`（基础设施）
- `dispatch` 和 `dmp` 各自依赖 `core`，互不依赖
- 业务工程只需引入 `open-ground-starter-land`（pom 类型）即可获得全部能力

### 与 open-ground 框架的关系

land 复用 open-ground-common 的公共能力，不重复造轮子：

| common 组件 | 在 land 中的用途 |
|-------------|-----------------|
| `DynamicJdbcTemplate` | 获取目标库 JdbcTemplate、执行存储过程、分页查询 |
| `DynamicDataSourceManager` | 获取数据库类型、动态建源 |
| `SysDatasourceMapper` | 按数据源 ID 查询 `dsName` 用于路由 |
| `TableMetadataService` | 获取目标表字段元数据（FileToDb 自动列映射） |
| `KeyGenerator` | 雪花算法生成主键（`getInternalKey()`） |
| `CommonException` / `CommonResult` | 统一异常与响应包装 |
| `SecurityContextHolder` | 获取当前操作用户 |
| `OptLog` 注解 | Controller 操作日志 |

---

## §2 设计理念

land 在从旧 `land` 迁移到 open-ground 时确立了 6 条核心设计原则：

### 2.1 模块自治（Module Autonomy）

每个功能子模块拥有独立的 `@AutoConfiguration` + `META-INF/spring/*.imports`，各自扫描自身包，不跨模块扫描：

| 模块 | 自动装配类 | 扫描范围 |
|------|-----------|---------|
| core | `LandCoreAutoConfiguration`、`TaskConfiguration`、`LandDataSourceConfig` 等 | `io.github.openground.land`（Service/Job/Util） |
| dispatch | `LandDispatchAutoConfiguration` | `io.github.openground.land.dispatch`（Controller/Executor/Discovery） |
| dmp | `LandDmpAutoConfiguration` | `io.github.openground.land.dmp`（Controller/Executor/Service） |

引入某模块则其自动装配生效，不引入则无任何影响。

### 2.2 无 DAO 层

废弃旧项目的 DAO 接口 + LiqDaoSupport 模式。全部数据访问直接通过 MyBatis `BaseMapper<T>`（通用 CRUD）+ 手写 XML（复杂查询）。框架表通过 `@MapperScan(sqlSessionFactoryRef = "landSqlSessionFactory")` 绑定框架自有数据源。

### 2.3 DTO 替代 Map

Controller 和 Service 方法的入参使用专用 DTO（如 `TaskCenterRequest`、`TaskMonitorRequest`），替代旧代码的 `Map<String, Object>`。但 Job 执行参数仍保留 `Map<String, Object>`（动态参数，由 `taskMethod` 配置驱动）。

### 2.4 多数据源隔离 + @Primary 兜底

框架拥有独立数据源（`ground.land.datasource`），与业务系统的 `spring.datasource` 完全隔离。通过两个 `@Primary` 配置类解决多数据源场景下的 Bean 冲突。

### 2.5 DB 心跳服务发现替代注册中心

不依赖 Nacos/Eureka 等注册中心，通过 `TASK_DISPATCH_ACTIVE_HOST` 表的心跳记录实现轻量级服务发现。每个实例定时写心跳，转发时查询活跃主机列表。

### 2.6 JobEngine 极简契约

Job 的抽象只有一个方法 `execute(Map<String, Object>) -> JobOut`，无 init/destroy 钩子。生命周期由调度层（线程）通过更新日志状态来框定，Job 自身不感知。

---

## §3 调度引擎核心

### 3.1 线程模型架构

调度采用 **定时扫描 + 线程池异步执行** 的混合模式：

```
应用启动
  │
  ▼
TaskCenterStartThread (InitializingBean)
  │  afterPropertiesSet(): 读取 task.dispatchCenter.cpsInit 开关，
  │                         初始化主机 IP / 调度组
  │  init():
  │    ├── 创建 ScheduledThreadPoolExecutor (线程名 DisPatch-Main-*)
  │    │     schThreadSize 个线程 (默认 2)
  │    ├── 创建 ThreadPoolExecutor (线程名 DisPatch-Task-*)
  │    │     corePoolSize/maxPoolSize/queueSize (默认 10/100/1000)
  │    ├── scheduleAtFixedRate(TaskDispatchMainThread,    延迟1分钟, 周期30秒)
  │    └── scheduleAtFixedRate(TaskDispatchConditionThread, 延迟1分钟, 周期60秒)
  │
  ▼
┌─────────────────────────┐    ┌──────────────────────────────┐
│  TaskDispatchMainThread  │    │ TaskDispatchConditionThread   │
│  (定时任务调度, 30s)      │    │ (条件依赖调度, 60s)            │
│                          │    │                               │
│  1. 写心跳到 active_host  │    │  1. 检查引擎开关              │
│  2. 检查本机引擎开关      │    │  2. 查询 isBefore='C' 的任务  │
│  3. 迁移失活主机待办任务  │    │  3. 逐个提交 ConditionJobThread│
│  4. 扫描到期任务->生成执行 │    │     (间隔3秒)                 │
│     计划(P), 推进下次时间 │    │                               │
│  5. 取P状态计划->提交      │    │                               │
│     StartTaskThread      │    │                               │
└───────────┬──────────────┘    └──────────────┬───────────────┘
            │                                  │
            ▼                                  ▼
     ┌──────────────────────────────────────────────┐
     │        ThreadPoolExecutor (DisPatch-Task-*)   │
     │                                               │
     │  StartTaskThread    ConditionJobThread        │
     │  (定时/依赖触发)     (条件触发: 检查文件到位)   │
     │       │                   │                   │
     │       ▼                   ▼                   │
     │  SpringUtil.getBean(taskMethod)               │
     │  -> JobEngine.execute(param)                   │
     │  -> JobOut                                     │
     │  -> 回写状态 S/F + exeAfterTask 链式触发        │
     └──────────────────────────────────────────────┘
```

### 3.2 七个核心类职责

| 类名 | 类型 | 职责 |
|------|------|------|
| `TaskCenterStartThread` | InitializingBean | 调度引擎启动器，创建线程池、注册定时任务、初始化主机标识 |
| `TaskDispatchMainThread` | Runnable | 定时任务调度主线程，扫描到期任务、生成执行计划、提交执行 |
| `TaskDispatchConditionThread` | Runnable | 条件依赖调度线程，扫描条件任务并提交检查 |
| `ConditionJobThread` | Thread | 条件任务执行，检查文件到位（SFTP/FTP/本地）后触发执行 |
| `StartTaskThread` | Runnable | 任务实际执行入口，调用 JobEngine 并回写结果，触发后置依赖链 |
| `ExecuteJobThread` | Thread | 手工异步执行，供 Controller 手动触发使用，不触发后置链 |
| `TaskDispatchServiceUtil` | 工具类 | 持有全局状态（cpsGroup/hostIp/taskConfig/线程池），提供 initJob/exeAfterTask/getBatchNo 等方法 |

### 3.3 四种触发链路

```
1. 定时触发:
   ScheduledThreadPool -> TaskDispatchMainThread.run()
     -> 扫描到期任务 -> 生成执行计划(P) -> 提交 StartTaskThread
     -> JobEngine.execute() -> 回写 S/F -> exeAfterTask 链式触发后置任务

2. 条件触发:
   ScheduledThreadPool -> TaskDispatchConditionThread.run()
     -> 提交 ConditionJobThread -> 检查文件(SFTP/FTP/本地)是否到位
     -> 文件到位 -> exeTaskPlan() 检查前置任务全部成功
     -> 提交 StartTaskThread -> JobEngine.execute()

3. 手动触发:
   TaskCenterController.executeJob() -> ExecuteJobThread
     -> JobEngine.execute() -> 回写 S/F (不触发后置链)

4. 依赖触发:
   StartTaskThread 执行成功 -> TaskDispatchServiceUtil.exeAfterTask()
     -> 检查后置任务的前置是否全部成功 -> 提交 StartTaskThread
```

### 3.4 任务状态机

执行计划（`TASK_DISPATCH_EXE_LOG`）的状态流转：

```
           ┌─── W (未执行/等待初始化)
           │        │
           │        ▼ (作业初始化)
           ▼──-> P (待处理) ──-> R (执行中) ──-> S (成功)
                                │              │
                                │              ├──-> F (失败) -> [自动重跑, 达上限则停用]
                                │              └──-> E (异常/超时告警)
                                │
                                └──-> [超时告警, 不自动终止]
```

- **W**：作业（Job）初始化时为后置任务生成的待执行计划
- **P**：定时扫描生成的待处理执行计划
- **R**：StartTaskThread 获取到任务后置为执行中
- **S/F**：Job 执行完成后回写
- **自动重跑**：`maxReExeTimes=-1` 无限重跑；否则递增 `reExeTimes`，超限则停用任务

### 3.5 分布式抢锁与故障转移

**抢锁机制**（`TaskDispatchMainThread.searchOtherHostTask()`）：
- 通过 `updateTaskDispatchConfig` 的更新计数判断是否抢到调度权
- 多主机同时扫描同一任务时，只有更新计数为 1 的主机获得调度权

**故障转移**（`TaskDispatchMainThread.updateTaskExeHostIp()`）：
- 每次扫描时检测活跃主机列表
- 将失活主机上状态为 `P`/`R` 的待办任务迁移到当前主机
- 确保主机宕机后任务不会丢失

---

## §4 内置 Job 体系

### 4.1 JobEngine 契约

```java
// open-ground-land-api/.../api/job/JobEngine.java:19
public abstract class JobEngine {
    public abstract JobOut execute(Map<String, Object> param) throws Exception;
}
```

- **唯一抽象方法**：`execute(Map<String, Object>)`，入参为动态参数 Map，返回 `JobOut`
- **无生命周期钩子**：Job 不感知 init/destroy，状态由调度线程管理
- **注册方式**：`@Service` 注解，Bean 名称即 `taskMethod` 配置值（类名首字母小写）
- **调用方式**：`SpringUtil.getBean(taskMethod)` 按名称获取 Bean 并执行

### 4.2 核心域对象

**JobOut**（`api/domain/JobOut.java:16`）-- Job 执行结果：

| 字段 | 类型 | 含义 |
|------|------|------|
| `success` | `Boolean` | 执行成功标志 |
| `message` | `String` | 执行信息（写库时截断到 1000 字节） |
| `resultmap` | `Map<String, Object>` | 扩展结果（内置 Job 基本未用） |

**JobContext**（`api/domain/JobContext.java:17`）-- 分段(step) Job 执行上下文：

> ⚠️ 当前版本暂未迁移分段体系，内置 Job 均不使用此对象。设计预留供未来分段处理使用。

| 字段 | 含义 |
|------|------|
| `jobParam` | Job 级参数（ConcurrentHashMap） |
| `stepParam` | 单个分段(step)级参数 |
| `executorContext` | 执行器内部上下文 |
| `finished` | 整个 Job 是否完成 |
| `stepSegmentExeInfo` | 当前分段执行信息 |

**StepSegmentExeInfo**（`api/domain/StepSegmentExeInfo.java:15`）-- 分段执行记录：

> ⚠️ 同属暂未迁移的分段体系，对应库表 `TASK_DISPATCH_STEP_LOG`。

| 字段 | 含义 |
|------|------|
| `segment` | 分段标识 |
| `taskPlanId` | 执行计划 ID |
| `stepId` | 分段 ID |
| `exeUrl` | 执行器地址 |
| `threadName` | 线程名 |
| `infoMessage` / `exceptionMessage` | 正常/异常信息 |
| `stepStatus` | 状态（`STATUS_SUCCESS` / `STATUS_FAIL`） |
| `finished` | 是否完成 |

### 4.3 内置 Job 逐个说明

所有内置 Job 位于 `open-ground-land-core/.../job/`，均 `extends JobEngine` 并加 `@Service`，Bean 名称即类名首字母小写（如 `startJob`、`shellTask`）。

| Job 类名 | Bean 名 | 用途 | 必填参数 | 可选参数 | 适用场景 |
|----------|---------|------|----------|---------|---------|
| `StartJob` | `startJob` | 作业流程起始标记，直接返回成功 | 无 | 无 | 作业链的第一个节点，仅作占位 |
| `EndJob` | `endJob` | 日切作业，将系统跑批日期切换到下一工作日 | `taskPlanId`、`cpsGroup` | `isAutoExe`、`sysEodDate` | 批处理结束后的日切，调用 `activeHostService.updateSysEodDate` |
| `EtlEndJob` | `etlEndJob` | ETL 结束标记，生成 `.ok` 文件 | 无 | `endFilePath`、`endFileName`、`sysEodDate` | ETL 跑批完成后生成完成标识文件 |
| `HttpJob` | `httpJob` | 发送 HTTP POST 请求 | `url`、`requestBody` | `timeout`、`sysEodDate` | 调用外部 HTTP 接口，自动替换 `${sysEodDate}` 占位符，解析 `sysHead.retStatus` |
| `ShellTask` | `shellTask` | 执行 Shell 脚本 | `shellPath` | `shellParam`、`bash`、`sysEodDate` | 执行单个 Shell 脚本，默认用 `sh` |
| `CommandTask` | `commandTask` | 在指定工作目录执行 Shell 命令（支持多命令逗号分隔） | `workPath`、`command` | `sysEodDate` | 批量执行 Shell 命令，用 `sh -c` 逐条执行 |
| `ProcedurelTask` | `procedurelTask` | 调用数据库存储过程 | `procName` | `sysEodDate`、`dsName` | 调用存储过程，`dsName` 指定时走 DynamicJdbcTemplate，否则走 Mapper XML |
| `BatchProcedurelTask` | `batchProcedurelTask` | 并发调用多个存储过程 | `procNames`（逗号分隔） | `dsName`、`sysEodDate`、`taskPlanId` | 批量执行存储过程，用 `landTaskExecutor` 并发，结果写入 attacheds |
| `BatchShellTask` | `batchShellTask` | 并发执行多个 Shell 脚本 | `shellPath`、`shellFiles`（逗号分隔） | `shellParam`、`bash`、`sysEodDate`、`taskPlanId` | 批量执行 Shell，每个脚本独立线程，结果写入 attacheds |
| `BatchFileImportJob` | `batchFileImportJob` | 批量导数任务，并发执行多个文件导入 | `jobConfigJsonFiles`（逗号分隔） | `ignoreException`（Y/N） | 批量文件导入，每个配置文件提交到线程池异步执行，调用 `fileImportJob` |
| `TestJob` | `testJob` | 测试任务，用于调试验证 | 无 | 无 | 开发调试用 |

**参数来源说明**：
- Job 执行参数来自 `TASK_DISPATCH_CONFIG` 表的 `taskParam` 字段（`key:value;` 格式）
- 调度层自动注入的隐含参数：`sysEodDate`（跑批日期）、`taskPlanId`（执行计划 ID）、`cpsGroup`（调度组）、`taskConfig`（配置对象）
- 动态变量：`${sysEodDate}`、`${machineDate}` 会在执行前被替换

### 4.4 JobFilter 机制

```java
// open-ground-land-core/.../job/filter/JobFilter.java:13
public interface JobFilter<T> {
    boolean filter(Map<String, Object> context, T data);  // true=接受, false=拒绝
}

// open-ground-land-core/.../job/filter/FileImportJobFilter.java:11
public abstract class FileImportJobFilter<T> implements JobFilter<T> { }
```

- **作用**：在执行 Job 前对数据进行过滤/校验
- **当前实现**：仅有 `FileImportJobFilter` 抽象基类，无具体实现（设计预留，供文件导入场景扩展）
- **注册方式**：子类加 `@Component` 即可被 Spring 扫描

### 4.5 二次开发：新增自定义 Job

**步骤一**：创建 Job 类

```java
package com.yourcompany.land.job;

import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
public class MyCustomJob extends JobEngine {

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        try {
            // 从 param 获取参数
            String bizDate = (String) param.get("sysEodDate");
            String customParam = (String) param.get("myParam");

            // 执行业务逻辑
            doWork(bizDate, customParam);

            out.setSuccess(true);
            out.setMessage("自定义任务执行成功: " + bizDate);
        } catch (Exception e) {
            log.error("自定义任务执行失败", e);
            out.setSuccess(false);
            out.setMessage(e.getMessage());
        }
        return out;
    }
}
```

**步骤二**：在 `TASK_DISPATCH_CONFIG` 表配置任务

| 关键字段 | 值 | 说明 |
|---------|-----|------|
| `taskMethod` | `myCustomJob` | 必须等于 Bean 名称（类名首字母小写） |
| `taskParam` | `myParam:value1;` | 自定义参数，`key:value;` 格式 |
| `exePeriodValue` / `exePeriodType` | `30` / `SECOND` | 执行周期 |
| `isBefore` | `N` | 定时任务用 `N`，条件依赖用 `C` |

**步骤三**：确保被 Spring 扫描

Job 类所在的包需要在 `@ComponentScan` 范围内。如果包名以 `io.github.openground.land` 开头，会被 `LandCoreAutoConfiguration` 自动扫描；否则需在业务工程自行配置扫描。

**关键约定**：
- `taskMethod` 值必须与 `@Service` Bean 名称完全一致
- 返回 `JobOut.success=false` 会触发自动重跑（若配置了 `maxReExeTimes`）
- 抛异常会被 `StartTaskThread` 捕获并记为失败
- `param` 中的 `sysEodDate` 是系统跑批日期，由调度层自动注入

---

## §5 多数据源机制

### 5.1 数据源隔离设计

land 拥有**独立的框架数据源**，与业务系统的数据源完全隔离：

```
┌─────────────────────────────────────────────────┐
│                  Spring 容器                     │
│                                                  │
│  ┌─────────────────────┐  ┌───────────────────┐ │
│  │ 业务数据源            │  │ land 框架数据源     │ │
│  │ spring.datasource    │  │ ground.land.datasource│
│  │ (业务系统配置)        │  │ (调度表专用)        │ │
│  │                      │  │                    │ │
│  │ dataSource Bean      │  │ landDataSource Bean │ │
│  │ sqlSessionFactory    │  │ landSqlSessionFactory│ │
│  │ (Primary)            │  │ (框架专用)           │ │
│  └─────────────────────┘  └───────────────────┘ │
│                                                  │
│  ┌─────────────────────────────────────────────┐ │
│  │ DynamicDataSourceManager (common 提供)       │ │
│  │ 动态业务数据源，按 dsName 路由               │ │
│  │ 供 DynamicJdbcTemplate 使用                  │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

- **框架数据源**（`ground.land.datasource`）：存储 `TASK_DISPATCH_*` 调度表，由 `LandDataSourceConfig` 配置
- **业务数据源**（`spring.datasource`）：业务系统的数据库，由 common 的 `DynamicDataSourceManager` 管理动态路由
- **业务动态数据源**：通过 `DynamicJdbcTemplate.getJdbcTemplate(dsName)` 按名称获取，用于存储过程调用、数据交换等

### 5.2 六个配置类

所有配置类通过 Spring Boot 3.x 的 `AutoConfiguration.imports` 机制注册：

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
io.github.openground.land.config.TaskConfiguration
io.github.openground.land.config.LandDataSourceConfig
io.github.openground.land.config.DefaultSqlSessionFactoryPrimaryConfig
io.github.openground.land.config.LandCoreAutoConfiguration
io.github.openground.land.config.LandDataSourcePrimaryConfig
```

| 配置类 | 职责 | 关键 Bean |
|--------|------|-----------|
| `LandCoreAutoConfiguration` | 组件扫描入口 | `@ComponentScan("io.github.openground.land")`，扫描 core 下所有 Service/Job/Component |
| `LandDataSourceConfig` | 框架独立数据源 | `landDataSource`(Druid, prefix=`ground.land.datasource`)、`landSqlSessionFactory`(统一扫描 `classpath*:mapper/land/*.xml`，含 land-core 和 land-dmp 的 Mapper XML)、`landSqlSessionTemplate`、`landTransactionManager`；`@MapperScan("io.github.openground.land.mapper", sqlSessionFactoryRef="landSqlSessionFactory")` |
| `LandDataSourcePrimaryConfig` | 业务数据源 @Primary 兜底 | 将业务 `dataSource` 包装为 `@Primary` 的 `primaryDataSource`，条件：存在 `dataSource` 且无 `RoutingDataSource` |
| `DefaultSqlSessionFactoryPrimaryConfig` | 业务 SqlSessionFactory @Primary 兜底 | 将默认 `sqlSessionFactory`/`sqlSessionTemplate` 标记为 `@Primary`，确保未指定 `sqlSessionFactoryRef` 的业务 Mapper 正常工作 |
| `TaskConfig` | 配置属性绑定 | `@ConfigurationProperties(prefix="task")`，含 `taskCenter`(Map)、`dispatchCenter`(Map)、`activeHostTimeoutSeconds`(120)、`activeHostCleanupDays`(30)、`stepPool`(30/100/200) |
| `TaskConfiguration` | 调度引擎启动入口 | `taskCenterStartThread`(`initMethod="init"`)、`dateUtil`、`landTaskExecutor`(ThreadPoolTaskExecutor, 线程名 `STEP-WORK-`)；`@EnableScheduling` |

### 5.3 @Primary 双兜底原理

当业务工程同时存在业务数据源和 land 框架数据源时，Spring 容器中会有两个 `DataSource` 和两个 `SqlSessionFactory`。为避免冲突：

1. `LandDataSourcePrimaryConfig`：将业务 `dataSource` 包装为 `@Primary`，使未指定 `sqlSessionFactoryRef` 的业务 Mapper 自动使用业务数据源
2. `DefaultSqlSessionFactoryPrimaryConfig`：将默认 `sqlSessionFactory` 标记为 `@Primary`，确保 MyBatis 自动配置不冲突
3. land 的 Mapper 通过 `@MapperScan(sqlSessionFactoryRef = "landSqlSessionFactory")` 显式绑定框架数据源

### 5.4 配置示例

```yaml
# application.yml

# === 业务数据源（由 common 的 DynamicDataSourceManager 管理）===
spring:
  datasource:
    druid:
      # 主数据源
      master:
        url: jdbc:mysql://127.0.0.1:3306/business_db
        username: root
        password: xxx
      # 动态数据源（按 dsName 路由）
      datasource-list:
        - dsName: db_order
          url: jdbc:mysql://127.0.0.1:3306/order_db
        - dsName: db_report
          url: jdbc:oracle:thin:@127.0.0.1:1521:report

# === land 框架数据源（调度表专用，与业务隔离）===
ground:
  land:
    datasource:
      url: jdbc:mysql://127.0.0.1:3306/land_db
      username: root
      password: xxx
      driver-class-name: com.mysql.cj.jdbc.Driver

# === land 调度配置 ===
task:
  taskCenter:
    corePoolSize: 10          # 任务线程池核心数
    maxPoolSize: 100          # 任务线程池最大数
    queueSize: 1000           # 任务队列大小
    hostType: ip              # 主机标识类型: ip 或 HOSTNAME
  dispatchCenter:
    cpsInit: true             # 是否启动调度引擎
    schThreadSize: 2          # 调度扫描线程数
  activeHostTimeoutSeconds: 120   # 活跃主机心跳超时（秒）
  activeHostCleanupDays: 30       # 过期主机清理天数
  stepPool:
    coreSize: 30              # 子任务线程池核心数
    maxSize: 100              # 子任务线程池最大数
    queueCapacity: 200        # 子任务队列容量

spring:
  application:
    name: my-batch-app        # 调度组名（自动转大写作为 cpsGroup）
server:
  port: 8080                  # 主机端口（与 IP 组成主机标识）
```

**关键配置项说明**：
- `task.dispatchCenter.cpsInit`：必须为 `true` 才会启动调度引擎（创建线程池和定时任务）
- `spring.application.name`：自动转大写作为 `cpsGroup`（调度组名），用于服务发现和任务路由
- `task.taskCenter.hostType`：`ip`（默认）用 `IP:port` 作主机标识；`HOSTNAME` 用用户名作标识（适用于 IP 不固定场景）

---

## §6 服务转发（dispatch）

### 6.1 设计取舍

land 在迁移时**抛弃了旧的 Feign 远程调用机制**，改为 **共享中心库 + 本地直连 + 执行操作 HTTP 转发** 模式：

| 维度 | 旧方案（land-develop） | 新方案（open-ground-land） |
|------|----------------------|--------------------------|
| 服务发现 | Nacos DiscoveryClient | DB 心跳表 `TASK_DISPATCH_ACTIVE_HOST` |
| 远程调用 | Feign 声明式客户端 | RestTemplate HTTP 转发 |
| 数据访问 | 各实例独立数据源 | 共享中心库 + 本地直连 |
| 部署模式 | 微服务分离部署 | 可合并部署（无 cpsGroup 则本地执行）|

### 6.2 核心三件套

```
┌─────────────────────────────────────────────────────────┐
│                  RemoteTaskExecutor                      │
│         (api 层接口, 定义转发契约)                        │
│   CommonResult<?> execute(String cpsGroup, String action,│
│                           Object request)                │
└────────────────────────┬────────────────────────────────┘
                         │ 实现
                         ▼
┌─────────────────────────────────────────────────────────┐
│                DbRemoteTaskExecutor                      │
│             (dispatch 层实现)                            │
│                                                          │
│  1. DbServiceDiscovery.getAvailableHosts(cpsGroup)       │
│     -> 查 TASK_DISPATCH_ACTIVE_HOST 表                   │
│  2. 负载均衡选目标 (轮询 / 指定 IP)                       │
│  3. 清空 request.cpsGroup (防二次转发死循环)              │
│  4. RestTemplate POST -> http://targetHost/task/...      │
└────────────────────────┬────────────────────────────────┘
                         │ 依赖
                         ▼
┌─────────────────────────────────────────────────────────┐
│                  DbServiceDiscovery                      │
│             (dispatch 层服务发现)                         │
│                                                          │
│  getAvailableHosts(cpsGroup)                             │
│    -> ActiveHostService.getAvailableHostIps(cpsGroup)    │
│    -> 查活跃时间在阈值内的主机 IP 列表（默认最近3分钟）    │
└─────────────────────────────────────────────────────────┘
```

### 6.3 完整转发链路

以 `POST /task/taskcenter/executeJob` 为例：

```
客户端 POST /task/taskcenter/executeJob
  TaskCenterRequest { cpsGroup: "ORDER-SVC", taskId: "T001", ... }
        │
        ▼
TaskCenterController.executeJob()
        │
        ├─ cpsGroup 非空 && remoteTaskExecutor != null ?
        │     ├── YES (需要转发)
        │     │     ▼
        │     │   remoteTaskExecutor.execute("ORDER-SVC", "/executeJob", request)
        │     │     │
        │     │     ▼ DbRemoteTaskExecutor
        │     │     1. dbServiceDiscovery.getAvailableHosts("ORDER-SVC")
        │     │        -> ["10.0.0.1:8080", "10.0.0.2:8080"]
        │     │     2. 轮询选目标: hosts[counter % size]
        │     │        -> "10.0.0.1:8080"
        │     │     3. 清空 request.cpsGroup = null (防循环)
        │     │     4. POST http://10.0.0.1:8080/task/taskcenter/executeJob
        │     │        (请求头加 encrypt=false, 内部请求不解密)
        │     │     5. 返回 CommonResult
        │     │
        │     └── NO (合并部署或无 cpsGroup)
        │           ▼
        │         taskCenterService.executeJob(request)  (本地执行)
        │
        ▼
目标实例收到请求 (cpsGroup 已被清空)
  -> 本地执行 -> 返回结果
```

**防死循环机制**：转发前清空 `request.cpsGroup`，目标实例收到时 `cpsGroup` 为 null，走本地执行分支，不会再次转发。

### 6.4 Controller 端点总览

#### TaskCenterController（`/task/taskcenter`）

| 端点 | 转发策略 | 说明 |
|------|---------|------|
| POST `/query` | 本地 | 任务列表查询 |
| POST `/add` | 本地 | 新增任务 |
| POST `/update` | 本地 | 修改任务 |
| POST `/delete` | 本地 | 删除任务 |
| POST `/batchUpdate` | 本地 | 批量修改任务 |
| POST `/queryhostlist` | 本地 | 查询主机列表 |
| POST `/engine` | 本地 | 引擎启停操作 |
| POST `/queryExeLogList` | 本地 | 查看执行日志列表 |
| POST `/queryJobExeLogList` | 本地 | 查询作业执行信息列表 |
| POST `/updateTaskExeLog` | 本地 | 修改执行计划 |
| POST `/executeJob` | **条件转发** | 手动执行任务（cpsGroup 非空则转发） |
| POST `/initJob` | **条件转发** | 手工初始化作业 |
| POST `/exeJobPlan` | **条件转发** | 手工执行任务计划 |
| POST `/executeJobSync` | **条件转发** | 手动执行任务（同步返回结果） |

**转发判定规则**：`cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null` 时转发，否则本地执行。

#### TaskDispatchParamController（`/task/taskcenter/param`）

全部本地执行（调度参数 CRUD）：`/query`、`/add`、`/update`、`/delete`、`/queryOptions`、`/queryCpsGroup`

#### TaskMonitorController（`/task/taskcenter`）

| 端点 | 转发策略 | 说明 |
|------|---------|------|
| POST `/getCpsServiceList` | 本地 | 查询调度组服务列表 |
| POST `/threadPoolMonitor` | **条件转发** | 线程池监控（转发到目标实例采集） |
| POST `/taskDashboard` | 本地 | 任务仪表盘 |

#### TaskSegmentController（`/task/taskcenter/segment`）

| 端点 | 转发策略 | 说明 |
|------|---------|------|
| POST `/execute` | **条件转发** | 分段执行（当前本地抛 UnsupportedOperationException，待迁移） |
| POST `/query` | 本地 | 查询分段步骤日志 |
| POST `/cleanContext` | 本地 | 清理上下文 |

### 6.5 cpsGroup 与 active_host 机制

**cpsGroup（调度组）**：
- 来源：`spring.application.name` 自动转大写
- 作用：标识一个调度组实例群，同一 cpsGroup 下的实例互为副本
- 使用：任务配置中指定 `cpsGroup` 决定任务由哪个调度组执行；转发时按 cpsGroup 查找可用实例

**active_host（活跃主机心跳）**：
- 每个实例启动后，`TaskDispatchMainThread` 每次扫描（默认 30 秒）向 `TASK_DISPATCH_ACTIVE_HOST` 表写心跳
- 心跳内容：`cpsGroup`、`hostIp`、`activeTime`（心跳时间）、`activeStatus`（ON/OFF）
- 活跃判定：`activeStatus = 'ON'` 或 `activeTime` 在最近 `activeHostTimeoutSeconds`（默认 120 秒）内
- 引擎开关：可通过 `/engine` 端点停启某实例的引擎（停止后不再扫描和执行任务，但仍保留心跳记录）

---

## §7 DMP 数据交换

### 7.1 模块定位

`open-ground-land-dmp` 是 land 的"数据管理平台"子模块，提供文件↔库、库↔库之间的数据交换能力，支持定时调度和手动触发两种执行方式。

### 7.2 三种交换类型

```java
// open-ground-land-dmp/.../entity/TaskType.java:9
public enum TaskType {
    FILE_TO_DB("文件->库"),   // 文件入库
    DB_TO_FILE("库->文件"),   // 库导出文件
    DB_TO_DB("库->库");       // 跨库迁移
}
```

### 7.3 DataExchangeJob 执行流程

`DataExchangeJob` 继承 `JobEngine`，是接入 land 调度框架的入口：

```
DataExchangeJob.execute(param)
  │
  ├── 含 configIds (逗号分隔)?
  │     ├── YES -> 批量模式: 逐个 configId 提交到 landTaskExecutor 异步执行
  │     │         立即返回 "已提交 N 个任务"
  │     │
  │     └── NO  -> 单任务模式:
  │               │
  │               ▼ configMapper.selectById(configId) 加载配置
  │               │
  │               ├── taskType = FILE_TO_DB -> fileToDbExecutor.execute(config)
  │               ├── taskType = DB_TO_FILE -> dbToFileExecutor.execute(config)
  │               └── taskType = DB_TO_DB   -> dbToDbExecutor.execute(config)
  │
  └── 封装 JobOut (success/message/resultmap)
```

### 7.4 四个执行器实现要点

#### DbToDbExecutor（库→库迁移）

- **模型**：生产者-消费者，`BlockingQueue` 容量 50 页，`threadCount` 个消费者线程（`DbWriterTask`）
- **数据源解析**：通过 `SysDatasourceMapper.selectById(dsId)` 获取 `dsName`，再用 `dynamicJdbcTemplate.getJdbcTemplate(dsName)` 获取目标库连接
- **三种导出模式**：`FULL_TABLE`（整表 `SELECT *`）、`CONDITIONAL`（追加 WHERE 条件）、`CUSTOM_SQL`（自定义 SQL）
- **列映射**：优先解析配置 JSON，否则从源库取样首行自动同名映射
- **写入容错**：整批 `batchUpdate` 失败后逐条重试，失败行写入 `*.err` 错误日志文件
- **TRUNCATE 模式**：`writeMode=TRUNCATE` 时先清空目标表再写入
- **类型转换**：通过 `SqlUtils.convertValue()` 统一处理，支持 INTEGER/BIGINT/DECIMAL/FLOAT/BOOLEAN/DATE/TIMESTAMP/TIME/VARCHAR 等类型，覆盖 MySQL/Oracle/PostgreSQL/DM/GaussDB 跨数据库类型名

#### DbToFileExecutor（库→文件导出）

- **流程**：解析路径（含日期占位符）→ 分页查询源库 → 写 `.tmp` 临时文件 → 重命名为正式文件 → 可选生成 `.ok` 标识文件
- **`.ok` 文件内容**：`filename`、`rows`、`checksum`(MD5)、`batchNo`、`createTime`、`configId`
- **表头**：`headerEnabled="1"` 时输出列名行
- **值格式化**：日期统一格式，含分隔符/换行的值用双引号包裹并转义

#### FileToDbExecutor（文件→库导入）

- **模型**：生产者-消费者，`BlockingQueue<String[]>` 容量 10000，生产者读文件按行 `split(delimiter)` 入队
- **跳过规则**：跳过空行和 `#` 注释行
- **自动列映射**：通过 `TableMetadataService.getTableColumns(dsName, table)` 获取目标表字段，按列顺序自动映射（与 DbToDb 从源库取样不同）
- **写入容错**：与 DbToDb 相同的"整批失败→逐条重试→.err 日志"策略

#### DatePathResolver（日期路径解析）

静态工具类，用正则 `\$\{([^}]+)\}` 匹配占位符，按当前时间格式化替换：

| 占位符 | 输出示例 | 说明 |
|--------|---------|------|
| `${yyyyMMdd}` | 20260625 | 完整日期 |
| `${yyyy-MM-dd}` | 2026-06-25 | 带分隔符日期 |
| `${yyyyMM}` | 202606 | 年月 |
| `${yyyy}` | 2026 | 年 |
| `${MMdd}` | 0625 | 月日 |
| `${HHmmss}` | 143025 | 时分秒 |
| `${HH:mm:ss}` | 14:30:25 | 带分隔符时间 |

不识别的占位符原样保留。被所有 Executor 和 Controller 的 errorFile 端点共同使用。

### 7.5 数据交换配置实体

**TaskDataExchangeConfig**（表 `DMP_DATA_EXCHANGE_CONFIG`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `taskName` | String | 任务名称 |
| `taskType` | String | FILE_TO_DB / DB_TO_FILE / DB_TO_DB |
| `sourceDsId` | Long | 源数据源 ID |
| `targetDsId` | Long | 目标数据源 ID |
| `sourceQuery` | String | 源端 SQL / WHERE 条件 |
| `sourceTable` | String | 源表名（DB_TO_DB） |
| `sourceFilePath` | String | 源文件路径（FILE_TO_DB） |
| `targetTable` | String | 目标表名 |
| `targetFilePath` | String | 目标文件路径（DB_TO_FILE） |
| `fileDelimiter` | String | 文件分隔符 |
| `fileEncoding` | String | 文件编码 |
| `writeMode` | String | APPEND / TRUNCATE / MERGE |
| `batchSize` | Integer | 批大小 |
| `columnMappings` | String | 列映射 JSON |
| `threadCount` | Integer | 并行线程数 |
| `taskStatus` | String | ENABLED / DISABLED |
| `headerEnabled` | String | 0/1 是否输出表头 |
| `doneFileEnabled` | String | 0/1 是否生成 .ok 文件 |
| `exportMode` | String | FULL_TABLE / CONDITIONAL / CUSTOM_SQL |
| `createBy`/`createTime`/`updateBy`/`updateTime`/`delFlag` | | 审计字段 |

**TaskDataExchangeLog**（表 `DMP_DATA_EXCHANGE_LOG`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `configId` | Long | 关联配置 ID |
| `taskType` | String | 任务类型 |
| `startTime` / `endTime` | Date | 开始/结束时间 |
| `durationSeconds` | Integer | 耗时秒 |
| `runStatus` | String | SUCCESS / FAIL / RUNNING / PARTIAL |
| `rowCount` | Integer | 处理行数 |
| `errorMsg` | String | 错误信息 |

### 7.6 DMP Controller 端点

`TaskExchangeConfigController`（`/task/exchange/config`），全部 POST：

| 端点 | 说明 |
|------|------|
| `/list` | 配置分页查询（按 taskName/taskType/taskStatus 筛选） |
| `/get` | 配置详情 |
| `/save` | 新增/更新（id 为空则新增，用 `KeyGenerator.getInternalKey()` 生成主键） |
| `/delete` | 逻辑删除 |
| `/execute` | 手动执行单任务（异步，返回 logId + RUNNING 状态） |
| `/batchExecute` | 批量执行（ids 数组） |
| `/errorFile` | 下载 `.err` 异常文件（流式响应） |
| `/logList` | 执行日志分页查询 |
| `/lineage` | 数据数据流图（返回 nodes + edges，表级数据流） |

### 7.7 二次开发：新增数据交换类型

1. 在 `TaskType` 枚举中新增类型
2. 创建新的 Executor 类（参考 `DbToDbExecutor`），注入 `DynamicJdbcTemplate` 和 `SysDatasourceMapper`
3. 在 `DataExchangeJob.execute()` 中添加新类型的分发分支
4. 在 `TaskExchangeConfigController.executeTask()` 中添加对应执行逻辑

---

## §8 二次开发规范速查

### 8.1 包结构约定

```
io.github.openground.land
├── api                          # 接口契约层（land-api 模块）
│   ├── domain/                  #   域对象: JobOut, JobContext, StepSegmentExeInfo
│   ├── dto/                     #   传输对象: TaskCenterRequest, TaskMonitorRequest 等
│   ├── executor/                #   执行器接口: RemoteTaskExecutor
│   └── job/                     #   Job 抽象: JobEngine
│
├── common                       # 公共（land-core 模块）
│   ├── constants/               #   常量: ErrorCode, StatusCode, IStatusCode
│   ├── entity/                  #   实体: TaskDispatchConfigDomain, TaskDispatchExeLog 等
│   ├── exception/               #   异常: SystemException, BatchDuplicateException
│   └── util/                    #   工具: FileUtil, ShellUtil, TaskDateUtil, SftpUtil 等
│
├── config                       # 配置（land-core 模块）
│   ├── LandCoreAutoConfiguration    # 组件扫描
│   ├── LandDataSourceConfig         # 框架数据源
│   ├── LandDataSourcePrimaryConfig  # 业务数据源 @Primary
│   ├── DefaultSqlSessionFactoryPrimaryConfig  # SqlSessionFactory @Primary
│   ├── TaskConfig                   # 配置属性绑定 (prefix="task")
│   └── TaskConfiguration            # 调度引擎启动
│
├── core                         # 调度核心（land-core 模块）
│   ├── TaskCenterStartThread        # 引擎启动器
│   ├── TaskDispatchMainThread       # 定时调度主线程
│   ├── TaskDispatchConditionThread  # 条件调度线程
│   ├── ConditionJobThread           # 条件任务执行
│   ├── StartTaskThread              # 任务执行入口
│   ├── ExecuteJobThread             # 手动执行线程
│   └── TaskDispatchServiceUtil      # 全局状态 + 工具方法
│
├── job                          # 内置 Job（land-core 模块）
│   ├── StartJob / EndJob / EtlEndJob / HttpJob / TestJob
│   ├── ShellTask / CommandTask / ProcedurelTask
│   ├── BatchShellTask / BatchProcedurelTask / BatchFileImportJob
│   └── filter/                  #   JobFilter 接口 + FileImportJobFilter
│
├── mapper                       # Mapper 接口（land-core 模块）
│   └── (绑定 landSqlSessionFactory)
│
├── service                      # Service（land-core 模块）
│   ├── TaskCenterService            # 任务中心服务
│   ├── ActiveHostService            # 活跃主机服务
│   └── impl/                        # 实现类
│
├── dispatch                     # 调度接入层（land-dispatch 模块）
│   ├── controller/              #   4 个 Controller
│   ├── discovery/               #   DbServiceDiscovery
│   ├── executor/                #   DbRemoteTaskExecutor
│   └── config/                  #   LandDispatchAutoConfiguration
│
└── dmp                          # 数据交换（land-dmp 模块）
    ├── controller/              #   TaskExchangeConfigController
    ├── entity/                  #   TaskDataExchangeConfig, TaskDataExchangeLog, TaskType
    ├── executor/                #   DataExchangeJob, DbToDbExecutor, DbToFileExecutor, FileToDbExecutor, DatePathResolver
    ├── mapper/                  #   (绑定 landSqlSessionFactory)
    ├── service/                 #   TaskDataExchangeConfigService
    └── config/                  #   LandDmpAutoConfiguration
```

### 8.2 新增功能放置规则

| 需求 | 放置位置 | 模块 |
|------|---------|------|
| 新增自定义 Job | `io.github.openground.land.job` 或业务包 | land-core 或业务工程 |
| 新增 JobFilter | `io.github.openground.land.job.filter` | land-core |
| 新增数据交换类型 | `io.github.openground.land.dmp.executor` | land-dmp |
| 新增调度管理端点 | `io.github.openground.land.dispatch.controller` | land-dispatch |
| 新增数据交换端点 | `io.github.openground.land.dmp.controller` | land-dmp |
| 新增调度配置项 | `TaskConfig` + `task.*` 前缀 | land-core |

### 8.3 配置项速查（`task.*` 前缀）

| 配置路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `task.taskCenter.corePoolSize` | int | 10 | 任务线程池核心数 |
| `task.taskCenter.maxPoolSize` | int | 100 | 任务线程池最大数 |
| `task.taskCenter.queueSize` | int | 1000 | 任务队列容量 |
| `task.taskCenter.hostType` | String | `ip` | 主机标识类型（`ip` 或 `HOSTNAME`） |
| `task.dispatchCenter.cpsInit` | boolean | false | **是否启动调度引擎**（必须为 true 才会创建线程池） |
| `task.dispatchCenter.schThreadSize` | int | 2 | 调度扫描线程数 |
| `task.activeHostTimeoutSeconds` | int | 120 | 活跃主机心跳超时秒数 |
| `task.activeHostCleanupDays` | int | 30 | 过期主机清理天数 |
| `task.stepPool.coreSize` | int | 30 | 子任务线程池核心数 |
| `task.stepPool.maxSize` | int | 100 | 子任务线程池最大数 |
| `task.stepPool.queueCapacity` | int | 200 | 子任务队列容量 |
| `ground.land.datasource.*` | - | - | 框架数据源配置（url/username/password/driver-class-name） |

### 8.4 异常处理

land 使用两级异常体系：

**land 自有异常**（`core/common/exception`）：
- `SystemException`：系统异常
- `BatchDuplicateException`：批处理重复执行异常

**错误码**（`core/common/constants/ErrorCode`）：

| 常量 | 值 | 含义 |
|------|-----|------|
| `SUCCESS` | `0000` | 成功 |
| `FAIL` | `9999` | 失败 |
| `PARAMETER_ILLEGAL_ERROR` | `1002` | 参数非法 |

**与框架集成**：dmp 模块实际使用 open-ground-base 的 `CommonException`（字符串 code 如 `"514003"`、`"1000"`），Controller 统一返回 `CommonResult`。

### 8.5 业务工程引入方式

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter-land</artifactId>
    <version>${revision}</version>
    <type>pom</type>
</dependency>
```

引入后自动获得：
- `open-ground-starter-common`（传递 MyBatis/Redis/Web/Druid/PageHelper/SpringDoc/JWT）
- land 四个子模块（api/core/dispatch/dmp）
- 所有 AutoConfiguration 自动生效

**Spring Boot AutoConfiguration 注册机制**：
- core：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- dispatch：`META-INF/spring/io.github.openground.land.dispatch.config.LandDispatchAutoConfiguration.imports`
- dmp：`META-INF/spring/io.github.openground.land.dmp.config.LandDmpAutoConfiguration.imports`

---

## §9 核心库表速查

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `TASK_DISPATCH_CONFIG` | 任务配置主表 | `taskId`、`jobId`、`taskMethod`（Job Bean 名）、`exePeriodValue`/`exePeriodType`（执行周期）、`nextExeTime`（下次执行时间）、`isBefore`（N=定时/C=条件）、`beforeTask`/`afterTask`（前置/后置依赖）、`exeHostIp`（执行主机）、`maxReExeTimes`（最大重跑次数） |
| `TASK_DISPATCH_EXE_LOG` | 执行计划/日志 | `id`（计划ID）、`taskId`、`jobId`、`exeStatus`（P/R/S/F/E/W）、`sysEodDate`（跑批日期）、`errInfo`（执行信息）、`reExeTimes`（已重跑次数）、`batchNo`（批次号） |
| `TASK_DISPATCH_STEP_LOG` | 分段步骤日志 | `taskPlanId`、`stepId`、`segment`、`stepStatus`、`infoMessage`/`exceptionMessage`（当前版本暂未使用，设计预留） |
| `TASK_DISPATCH_ACTIVE_HOST` | 活跃主机心跳 | `cpsGroup`（调度组）、`hostIp`（主机标识）、`activeTime`（心跳时间）、`activeStatus`（ON/OFF）、`sysEodDate`（当前跑批日期） |
| `TASK_DISPATCH_PARAM` | 调度参数 | `paramId`、`paramName`、`cpsGroup`、`hostIp`、`port` |
| `TASK_DATA_FILE_DIR` | 文件目录管理 | `dirName`（目录名称）、`dirPath`（目录路径）、`dirType`（LOCAL/SFTP/FTP）、`remark`（备注），统一管理数据交换文件目录，配置任务时选择目录+输入文件名 |
| `DMP_DATA_EXCHANGE_CONFIG` | 数据交换配置 | `taskType`、`sourceDsId`/`targetDsId`、`sourceQuery`/`sourceTable`/`sourceFilePath`、`sourceFileDirId`/`targetFileDirId`（关联文件目录）、`sourceSystem`（文件来源系统）、`targetTable`/`targetFilePath`、`writeMode`、`columnMappings`、`exportMode` |
| `DMP_DATA_EXCHANGE_LOG` | 数据交换执行日志 | `configId`、`runStatus`（SUCCESS/FAIL/RUNNING/PARTIAL）、`rowCount`、`startTime`/`endTime`、`errorMsg` |

---

## §10 LLM 快速上下文

### 一段话理解 land

open-ground-land 是一个分布式批处理调度引擎。它通过 `TaskCenterStartThread` 启动两个定时扫描线程（定时任务30秒/条件任务60秒），扫描 `TASK_DISPATCH_CONFIG` 表中到期或条件满足的任务，生成执行计划写入 `TASK_DISPATCH_EXE_LOG`，再通过线程池提交 `StartTaskThread` 执行。执行时通过 `SpringUtil.getBean(taskMethod)` 获取 `JobEngine` 子类 Bean 并调用 `execute(Map)`。多实例部署时通过 `TASK_DISPATCH_ACTIVE_HOST` 表的心跳实现服务发现，执行类操作通过 `DbRemoteTaskExecutor` 用 RestTemplate HTTP 转发到目标实例，数据类操作直接本地执行。框架使用独立数据源 `ground.land.datasource` 与业务数据源隔离。

### 常见任务代码定位

| 需求 | 关键文件 |
|------|---------|
| 修改调度周期/扫描频率 | `TaskCenterStartThread.java`（`scanningPeriod`/`conditionScanningPeriod`）+ `application.yml`（`task.taskCenter`/`task.dispatchCenter`） |
| 新增 Job 类型 | `open-ground-land-core/.../job/` 下新建类 `extends JobEngine` + `@Service` |
| 修改任务执行流程 | `StartTaskThread.java`（执行入口）、`TaskDispatchServiceUtil.java`（依赖链） |
| 修改远程转发逻辑 | `DbRemoteTaskExecutor.java`、`DbServiceDiscovery.java` |
| 新增 REST 端点 | `open-ground-land-dispatch/.../controller/` 或 `open-ground-land-dmp/.../controller/` |
| 修改数据源配置 | `LandDataSourceConfig.java` + `application.yml`（`ground.land.datasource`） |
| 新增数据交换类型 | `TaskType.java` + `open-ground-land-dmp/.../executor/` 新建 Executor + `DataExchangeJob.java` 分发 |
| 修改任务状态机 | `StartTaskThread.java`（状态回写）、`TaskDispatchExeLogMapper.xml`（状态查询/更新） |
| 排查任务不执行 | 检查 `task.dispatchCenter.cpsInit=true`、引擎开关 ON、`activeHostTimeoutSeconds` 内有心跳、`nextExeTime` 已到期 |
| 修改线程池参数 | `TaskCenterStartThread.init()`（调度线程池）+ `application.yml`（`task.taskCenter`/`task.stepPool`） |

### 关键文件索引

| 文件 | 行号 | 说明 |
|------|------|------|
| `JobEngine.java` | :19 | Job 抽象类，唯一方法 `execute(Map)->JobOut` |
| `RemoteTaskExecutor.java` | :15 | 远程执行器接口 |
| `TaskCenterStartThread.java` | :72 | 调度引擎初始化入口 |
| `TaskDispatchMainThread.java` | :65 | 定时任务调度主线程 |
| `TaskDispatchServiceUtil.java` | :32 | 全局状态 + initJob/exeAfterTask |
| `StartTaskThread.java` | :67 | 任务执行入口 |
| `DbRemoteTaskExecutor.java` | :63 | 远程任务转发实现 |
| `DbServiceDiscovery.java` | :35 | DB 服务发现 |
| `TaskCenterController.java` | :34 | 任务中心 Controller |
| `LandDataSourceConfig.java` | :48 | 框架数据源配置 |
| `TaskConfig.java` | :19 | 配置属性绑定（prefix=`task`） |
| `DataExchangeJob.java` | :25 | 数据交换 Job 入口 |
| `FileToDbExecutor.java` | :31 | 文件→库执行器，类型转换委托 `SqlUtils` |
| `DbToDbExecutor.java` | :31 | 库到库执行器，类型转换委托 `SqlUtils` |
| `TaskType.java` | :9 | 数据交换类型枚举 |
| `SqlUtils.java` (common-core) | :187 | 数据库类型值转换 `convertValue`，dmp 执行器共用 |

---

> **文档说明**：本文档基于 open-ground-land 源码分析编写，涵盖设计理念、架构分层、内置 Job、多数据源、服务转发、数据交换、二次开发规范、库表速查与 LLM 上下文。分段处理体系（Step/Segment/IReader）当前版本暂未迁移，文中已标注，待后续版本补充。

