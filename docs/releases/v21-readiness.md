# OfferLab V21 后端发布证据

> 证据整理日期：2026-07-28
> 仓库：`wbn-2020/offerlab-java`
> 工作分支：`feature/v21-release-readiness`
> 冻结基线：`dev-v2@25d2cef8b5f91ea85d3cae721878a63cdfa812bf`
> 证据编写起点 HEAD：`25d2cef8b5f91ea85d3cae721878a63cdfa812bf`
> V21 生产代码 SHA：`25d2cef8b5f91ea85d3cae721878a63cdfa812bf`（V21 后端仅新增证据文档）
> 当前结论：`PASS_WITH_BLOCKERS`，不得标记为 `RELEASABLE`

## 1. 证据口径

本文只记录可追溯事实，并严格区分：

1. 已绑定到冻结基线 `25d2cef` 的 GitHub Actions 成功事实。
2. 2026-07-28 在 V21 当前工作树上实际执行的 Maven、migration 和 workflow Guard 结果。
3. 需要在 V21 最终提交和 pull request 上刷新的 Git/CI 证据。
4. 没有真实环境而保持 `BLOCKED` 的动态场景。

本轮已运行 Maven 和一次性静态检查；没有启动应用、数据库、容器、浏览器或其他常驻服务。当前工作树结果只能支持本地静态结论，不能替代最终 pull request CI 或动态验收。

状态判定：

| 对象 | 当前状态 | 说明 |
|---|---|---|
| 冻结生产代码基线 `25d2cef` | `STATIC_VERIFIED` | 同 SHA 的 Backend CI 与 Dependency Audit 已成功 |
| V21 后端交付分支 | `LOCAL_STATIC_VERIFIED` | 当前工作树 Maven、migration Guard 和静态范围验证已通过；最终提交 SHA 与分支 CI 待补 |
| 动态验收 | `BLOCKED` | 本任务未配置或启动真实依赖环境 |
| 发布决策 | `PASS_WITH_BLOCKERS` | 可交付静态证据，不等于允许发布 |

## 2. 基线与分支证据

### 2.1 本地基线

证据收集时观察到：

| 字段 | 值 |
|---|---|
| 分支 | `feature/v21-release-readiness` |
| HEAD | `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| 父提交 | `cc6e01e98e93a0cdd565c4c1e45df5b9db2ace43` |
| 提交主题 | `fix: preserve dependency audit progress` |
| 作者/提交者 | `nqx <1741847900@qq.com>` |
| 提交时间 | `2026-07-28T02:54:06+08:00` |
| 本地 `dev-v2` | `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| `origin/dev-v2` | `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| V21 分支起点差异 | 空，分支从冻结基线原样创建 |
| 整理前工作区 | clean |
| V21 本地分支 upstream | 未设置 |
| 远端同名 V21 分支 | 证据收集时不存在 |

V21 方案中的冻结 PR 为后端 PR #17。GitHub API 在 `2026-07-28T17:04:28+08:00` 前后的观察结果为：

| 字段 | 值 |
|---|---|
| PR | `#17` |
| 状态 | open |
| 范围 | `dev-v2 -> main` |
| Head SHA | `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| URL | `https://github.com/wbn-2020/offerlab-java/pull/17` |

以上 PR 状态是带时间点的历史观察。主 Agent 必须在 V21 最终交付前重新确认 PR #17 的 HEAD 未被 V21 提交扩展。

### 2.2 最终分支占位

| 字段 | 主 Agent 填写 |
|---|---|
| 最终分支 HEAD | 由 V21 PR head 元数据记录；生产代码保持 `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| 提交时间 | 由包含本文的 Git commit 元数据记录 |
| V21 PR 编号 | `PENDING_CREATION` |
| V21 PR 范围 | `feature/v21-release-readiness -> dev-v2` |
| 最终工作区状态 | `[clean / 非 clean 及原因]` |
| `git diff --name-status 25d2cef...HEAD` | 预期仅 `A docs/releases/v21-readiness.md`，提交后复核 |
| PR #17 复核时间与 Head SHA | 2026-07-28 已复核为 `25d2cef8b5f91ea85d3cae721878a63cdfa812bf`，最终推送后再次复核 |

## 3. 无生产改动声明

V21 后端方案要求默认不写生产文件。本任务遵守该边界：

- 不修改 Java 源码或测试。
- 不修改 SQL、Flyway 资源、manifest 或数据库脚本。
- 不修改根 POM、模块 POM 或依赖版本。
- 不修改 GitHub Actions workflow。
- 不修改脚本、配置或运行时资源。
- 唯一新增文件为 `docs/releases/v21-readiness.md`。

在最终提交前，主 Agent 必须复核以下路径没有相对 `25d2cef` 的差异：

```powershell
git diff --name-status 25d2cef8b5f91ea85d3cae721878a63cdfa812bf...HEAD
git diff --exit-code 25d2cef8b5f91ea85d3cae721878a63cdfa812bf...HEAD -- `
  '*.java' '*.sql' 'pom.xml' '**/pom.xml' `
  '.github/workflows/**' 'scripts/**' `
  'db/migration/**' 'community-bootstrap/src/main/resources/db/flyway/**'
```

预期唯一差异：

```text
A	docs/releases/v21-readiness.md
```

如果最终差异出现 API、DTO、Java、POM、依赖、migration、workflow 或脚本变化，本声明立即失效，V21 后端状态改为 `BLOCKED`，并重新评审跨仓库合同、迁移和回滚方案。

## 4. 当前模块、依赖与迁移边界

### 4.1 Maven Reactor

根 POM 声明 13 个子模块，加上父项目共 14 个 Reactor 项目：

1. `community-common`
2. `community-infrastructure`
3. `community-domain-user`
4. `community-domain-post`
5. `community-domain-interaction`
6. `community-domain-feed`
7. `community-domain-search`
8. `community-domain-question`
9. `community-domain-notification`
10. `community-domain-analytics`
11. `community-domain-incentive`
12. `community-archtest`
13. `community-bootstrap`

当前共有 14 个受 Git 跟踪的 `pom.xml` 文件。根 POM Git blob 为：

```text
pom.xml = 339c4fc589367c5ffd6927beefe5a34c2d76134f
```

构建基线：

| 项 | 当前值 |
|---|---|
| Java | 17 |
| Maven Enforcer | Java `>=17`、Maven `>=3.9.0` |
| 项目版本 | `1.0.0-SNAPSHOT` |
| Surefire/Failsafe | `3.5.6` |
| 集成测试 profile | `integration-tests` |
| SBOM profile | `sbom` |
| 依赖审计 profile | `security-audit` |

### 4.2 依赖锁定边界

当前根 POM 中与运行时和安全审计相关的主要锁定版本如下：

| 依赖或工具 | 版本 |
|---|---|
| Spring Boot BOM | `3.5.15` |
| Jackson BOM | `2.21.5` |
| Log4j BOM | `2.25.5` |
| Netty BOM | `4.1.136.Final` |
| Tomcat Embed | `10.1.57` |
| Logback | `1.5.35` |
| SnakeYAML | `2.5` |
| Commons Lang | `3.20.0` |
| MyBatis Plus | `3.5.17` |
| Redisson | `3.52.0` |
| Testcontainers BOM | `1.21.4` |
| Swagger UI | `5.32.9` |
| OWASP Dependency-Check | `12.2.2` |
| Dependency-Check 失败阈值 | CVSS `>= 7.0` |

这些版本属于冻结基线，不是 V21 新增或升级。完整传递依赖树没有在本任务中重新解析；同 SHA 的 CI 已生成 aggregate SBOM，但主 Agent 仍需把最终分支 CI 的 SBOM 与校验和登记到本文占位。

### 4.3 Migration 边界

当前迁移清单来自 `db/migration/flyway-manifest.json`：

| 项 | 当前值 |
|---|---|
| manifest 格式 | `3` |
| 内容哈希算法 | `sha256-utf8-lf-no-bom-v1` |
| baseline version | `0` |
| schema history table | `flyway_schema_history` |
| core migration | `69`，自动迁移 |
| demo migration | `4`，不自动迁移 |
| migration 总数 | `73` |
| 最新 core version | `20260727.01` |
| 最新 demo version | `20260712.01` |
| demo history table | `flyway_demo_schema_history` |

Git 对象边界：

```text
db/migration tree = 529effe5f9f2719d940daf51187ff8f5b617bca2
db/migration/flyway-manifest.json = 4c9e50b19c4f3fdbc841c1d4abfe579c1d1525e2
community-bootstrap/src/main/resources/db/flyway tree =
  c073d018d6f1bfa5b9b5d65fd9816b93aa5927f3
```

V21 后端不新增 migration、不改写已登记 migration、不改变 core/demo 分流，也不产生数据库回滚脚本。最终分支若改变上述对象，必须停止使用本证据结论。

## 5. 已有测试与 CI 事实

### 5.1 本轮 Surefire 报告

`mvn -B -ntp test` 已在当前 V21 工作树执行，并刷新
`target/surefire-reports/TEST-*.xml`。报告文件时间范围为：

```text
2026-07-28 17:59:02+08:00 至 2026-07-28 18:03:15+08:00
```

执行时生产代码 HEAD 为冻结基线 `25d2cef8b5f91ea85d3cae721878a63cdfa812bf`，
工作树唯一未跟踪内容为本文。报告内没有嵌入 Git SHA，因此通过命令时间、执行前后
Git 范围和本节记录共同绑定；最终提交仍必须由 pull request CI 重新验证。

| 模块 | XML 报告 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| `community-archtest` | 8 | 37 | 0 | 0 | 0 |
| `community-bootstrap` | 56 | 323 | 0 | 0 | 0 |
| `community-domain-analytics` | 15 | 68 | 0 | 0 | 0 |
| `community-domain-feed` | 9 | 28 | 0 | 0 | 0 |
| `community-domain-incentive` | 4 | 12 | 0 | 0 | 0 |
| `community-domain-interaction` | 14 | 24 | 0 | 0 | 0 |
| `community-domain-notification` | 17 | 36 | 0 | 0 | 0 |
| `community-domain-post` | 67 | 190 | 0 | 0 | 0 |
| `community-domain-question` | 23 | 65 | 0 | 0 | 0 |
| `community-domain-search` | 23 | 58 | 0 | 0 | 0 |
| `community-domain-user` | 13 | 16 | 0 | 0 | 0 |
| `community-infrastructure` | 13 | 28 | 0 | 0 | 0 |
| **合计** | **262** | **885** | **0** | **0** | **0** |

当前工作区没有本轮 Failsafe XML；Testcontainers/Failsafe 结果由最终 pull request CI
生成。本组 885-test 数据可登记为当前工作树本地测试结果，但不得冒充最终提交的 CI
或集成测试结果。

### 5.2 V21 当前工作树本地静态验证

执行环境：

| 项 | 值 |
|---|---|
| OS | Windows 11 amd64 |
| Java | Amazon Corretto `17.0.19` |
| Maven | `3.9.9` |
| 执行日期 | 2026-07-28 |

当前工作树执行结果：

| 命令 | 结果 | 证据摘要 |
|---|---|---|
| `mvn -B -ntp test` | `PASSED` | 14 个 Reactor 项目全部 SUCCESS；2026-07-28 18:03:16 +08:00 完成；耗时约 4 分 40 秒 |
| Surefire XML 聚合 | `PASSED` | 262 份报告、885 tests、0 failures、0 errors、0 skipped；报告时间 17:59:02 至 18:03:15 +08:00 |
| `node ./scripts/test-ci-workflow.mjs` | `PASSED` | Backend CI workflow guard passed |
| `./scripts/test-migration-content-hash.ps1` | `PASSED` | Portable migration content hash fixtures passed |
| `./scripts/check-migration-safety.ps1` | `PASSED` | 73 项 Flyway 同步和 migration safety 检查通过 |
| `./scripts/test-migration-safety-policy.ps1` | `PASSED` | Migration safety policy fixtures passed |

Maven 根日志末尾显示的 `Tests run: 323` 是最后一个 `community-bootstrap`
模块的汇总，不是整个 Reactor 总数；V21 总测试数使用全部 Surefire XML 聚合得到的 885。

本地结果可以支持 `LOCAL_STATIC_VERIFIED`，但最终提交 SHA、clean 工作区和 GitHub
pull-request CI 尚未生成，因此不能单独升级为 `RELEASABLE`。

### 5.3 已知成功的 GitHub Actions

以下运行都绑定冻结基线 `25d2cef8b5f91ea85d3cae721878a63cdfa812bf`，不是 V21 最终文档提交的新运行：

| Workflow | Event | Run | 结果 | 完成时间 |
|---|---|---|---|---|
| Backend CI | `pull_request`，PR #17 | `30296061502` | success | `2026-07-28T03:01:20+08:00` |
| Backend CI | `push`，`dev-v2` | `30296058314` | success | `2026-07-28T03:01:40+08:00` |
| Backend Dependency Audit | `pull_request`，PR #17 | `30296061545` | success | `2026-07-28T06:29:34+08:00` |

可追溯链接：

- `https://github.com/wbn-2020/offerlab-java/actions/runs/30296061502`
- `https://github.com/wbn-2020/offerlab-java/actions/runs/30296058314`
- `https://github.com/wbn-2020/offerlab-java/actions/runs/30296061545`

PR Backend CI `30296061502` 的以下步骤均为 success：

- 后端和前端合同仓库 checkout。
- Java 17 设置。
- migration 资产、内容 hash 和破坏性 SQL policy 验证。
- 验证 migration 检查未改写工作树。
- `mvn -B -ntp test`。
- `community-infrastructure,community-domain-user` Testcontainers/Failsafe 集成验证。
- Failsafe 报告发现与结果门禁。工作流要求至少 5 份报告、至少 5 个测试，且 failure/error/skipped 均为 0。
- 可执行 JAR 与 aggregate SBOM 打包。
- SHA-256 校验和生成。
- 测试报告和不可变发布输入上传。

对应 artifact：

| Run | Artifact | Artifact ID |
|---|---|---:|
| `30296061502` | `offerlab-backend-test-reports` | `8664779124` |
| `30296061502` | `offerlab-backend-release-inputs` | `8664781014` |
| `30296058314` | `offerlab-backend-test-reports` | `8664788873` |
| `30296058314` | `offerlab-backend-release-inputs` | `8664790958` |
| `30296061545` | `backend-dependency-check` | `8670293991` |

Dependency Audit `30296061545` 的 Dependency-Check、报告上传和 SARIF 上传均成功。该事实证明 `25d2cef` 的审计工作流成功，不证明仓库 Secret 的值或存在状态，也不自动绑定未来 V21 提交。

## 6. 当前 CI 工作流边界

### 6.1 Backend CI

`.github/workflows/ci.yml` Git blob：

```text
5aea0f80525d718fffb69b2d9b5a0f1e2bb08142
```

Workflow 在所有 pull request 上运行。V21 功能分支若以 `dev-v2` 为 base，前端合同 checkout 默认解析到前端 `dev-v2`，除非仓库变量 `OFFERLAB_FRONTEND_REF` 覆盖。

V21 本轮不改变 API、DTO、字段或 migration，因此使用对端 `dev-v2` 符合方案边界。主 Agent 必须从最终 CI 日志记录实际 checkout 的前端 commit SHA，不能只记录 ref 名称。

### 6.2 Dependency Audit

`.github/workflows/security.yml` Git blob：

```text
06f63ea9d619d9a45023f0e3357bb908f2e1088b
```

该 workflow：

- PR 仅在 `pom.xml`、任意模块 POM 或 security workflow 变化时自动触发。
- 还支持每周计划任务和 `workflow_dispatch`。
- 使用 Dependency-Check 日级缓存，允许恢复旧日期缓存。
- timeout 为 180 分钟。
- `cancel-in-progress: false`，避免新运行取消正在更新的审计。
- Maven 审计步骤先 `continue-on-error`，随后由独立步骤强制失败，最终失败不会被静默吞掉。

因此，只有文档差异的 V21 PR 不会自动产生新的 Dependency Audit。主 Agent 必须在以下两种方案中明确选择一种：

1. 对最终 V21 ref 手动执行 `workflow_dispatch`，记录当前 SHA 的成功 run。
2. 记录“等价依赖基线”豁免：证明最终 SHA 相对 `25d2cef` 没有 POM、源码、workflow、migration 或脚本变化，并引用成功 run `30296061545`。豁免必须有决策人、日期和有效期。

## 7. 验证命令与执行状态

以下命令区分为本轮已执行项和最终 pull request CI 待执行项。

### 7.1 最终范围冻结

```powershell
git rev-parse HEAD
git status --short
git merge-base --is-ancestor `
  25d2cef8b5f91ea85d3cae721878a63cdfa812bf HEAD
git diff --name-status `
  25d2cef8b5f91ea85d3cae721878a63cdfa812bf...HEAD
git diff --check
```

通过标准：

- HEAD 等于登记的最终 SHA。
- ancestor 检查退出码为 0。
- 最终差异只有本文。
- 工作区 clean。
- `git diff --check` 退出码为 0。

### 7.2 环境记录

```powershell
java -version
mvn -version
```

记录 Java vendor/version、Maven version、OS 和执行时间。

### 7.3 Migration 静态门禁

```powershell
node ./scripts/test-ci-workflow.mjs
./scripts/test-migration-content-hash.ps1
./scripts/check-migration-safety.ps1
./scripts/test-migration-safety-policy.ps1
git status --short
```

通过标准：

- 每条命令退出码为 0。
- manifest、canonical SQL 和 runtime Flyway 资源一致。
- 没有破坏性 SQL policy 违规。
- 执行后工作区仍 clean。

### 7.4 单元与定向测试

```powershell
mvn -B -ntp test
mvn -B -ntp -pl community-domain-post -am test
mvn -B -ntp -pl community-domain-analytics -am test
```

根级全量命令是必须项。定向命令用于刷新 V19/V20 主要后端范围，不可替代全量测试。

执行状态：

- `mvn -B -ntp test`：`PASSED`，885 tests，0 failure、0 error、0 skipped。
- 两条定向命令：`NOT_NEEDED`，根级 Reactor 已覆盖对应模块，且 V21 无后端生产代码变化。

### 7.5 Disposable middleware 集成测试

```powershell
mvn -B -ntp `
  -pl community-infrastructure,community-domain-user `
  -am -Pintegration-tests verify
```

通过标准与 CI 一致：至少 5 份 Failsafe XML、至少 5 个测试，failure/error/skipped 均为 0。Docker 或 Testcontainers 不可用时产生的跳过不能登记为通过。

本机状态：`NOT_RUN_BY_POLICY`。本轮按用户约束不启动 Docker 或中间件；最终结果由
pull request Backend CI 提供。

### 7.6 发布输入

```powershell
mvn -B -ntp -Psbom -DskipTests package
```

主 Agent 应优先引用 CI 上传的 JAR、aggregate SBOM JSON/XML 和 `offerlab-artifacts.sha256`，并登记 artifact ID 与 SHA-256。

本机状态：`NOT_RUN_BY_POLICY`。最终发布输入由 pull request Backend CI 构建和上传。

### 7.7 依赖审计

优先通过 GitHub Actions `Backend Dependency Audit` 的 `workflow_dispatch` 在最终 V21 ref 上执行。不要在日志、命令记录或本文中写入 `NVD_API_KEY` 的值。

V21 决策：使用等价生产基线豁免。V21 后端差异仅允许为本文；成功审计 run
`30296061545` 绑定相同生产代码、POM、workflow、脚本和 migration 基线 `25d2cef`。
若最终差异越过 docs-only 边界，豁免立即失效。

## 8. 主 Agent 必须刷新和填写的静态证据

| 编号 | 必填项 | 占位 |
|---|---|---|
| R1 | 最终 V21 SHA | 由 V21 PR head 记录；生产代码 SHA 固定为 `25d2cef8b5f91ea85d3cae721878a63cdfa812bf` |
| R2 | 最终差异仅本文 | `PASSED`；提交前 staged name-status 仅 `A docs/releases/v21-readiness.md` |
| R3 | Java/Maven 版本 | `Amazon Corretto 17.0.19 / Maven 3.9.9 / Windows 11 amd64` |
| R4 | `git diff --check` | `PASSED`；2026-07-28 提交前 `git diff --cached --check` exit 0，仅 LF/CRLF 工作副本提示 |
| R5 | migration 四项门禁 | `2026-07-28：4 条命令均 exit 0，73 项 migration 同步/安全检查通过` |
| R6 | 当前工作树 `mvn -B -ntp test` | `2026-07-28 18:03:16 +08:00；885/0/0/0；PASSED` |
| R7 | post 定向测试 | `NOT_NEEDED：根级全量 Reactor 已覆盖 community-domain-post` |
| R8 | analytics 定向测试 | `NOT_NEEDED：根级全量 Reactor 已覆盖 community-domain-analytics` |
| R9 | 当前 SHA Backend CI | `PENDING_PR_CI` |
| R10 | CI 实际前端合同 SHA | 预期 `dev-v2@6b92237c4c1c500ab0fbf53c76634dfbb80ba536`，以 CI 日志为准 |
| R11 | Failsafe 结果 | `PENDING_PR_CI` |
| R12 | 测试报告 artifact | `PENDING_PR_CI` |
| R13 | JAR、SBOM、checksum artifact | `PENDING_PR_CI` |
| R14 | Dependency Audit | 等价基线豁免：V21 后端仅文档差异，沿用 `25d2cef` 成功 run `30296061545` |
| R15 | PR #17 未被扩展 | 2026-07-28 初次复核通过；V21 推送后再次复核 |
| R16 | 独立 QA 结论 | `PASS_WITH_BLOCKERS`；无 P0；readiness 事实矛盾已修正；PR CI 与动态场景仍为 blocker |

填写规则：

- 第 5.1 节的 885 个测试只属于当前工作树本地结果，不得写成最终提交的 GitHub CI 或 Failsafe 结果。
- 不得把 `30296061502` 写成 V21 最终提交的新 CI；只能作为 `25d2cef` 基线证据或明确的等价基线。
- 所有 run 必须同时记录 `head_sha`，仅记录 workflow 名称或绿色截图不合格。
- 失败后重试时保留失败 run ID 和重试原因，不能只保留最后一次成功。

## 9. 动态验收场景

以下场景均未在本任务执行，当前状态统一为 `BLOCKED`。

| 编号 | 场景 | 必需环境或数据 | 通过证据 | 当前阻塞 |
|---|---|---|---|---|
| D-BE-01 | MySQL 8 新库执行全部 core Flyway | 空白专用库、69 个 core migration、禁止 demo location | Flyway history、schema readiness JSON、0 failed/mismatch/unexpected | 未提供专用数据库，未启动服务 |
| D-BE-02 | 既有库从登记 baseline 升级 | 可恢复快照、升级前 ledger、备份校验 | 升级日志、前后 reconciliation、恢复点 | 无可用升级库和备份 |
| D-BE-03 | 关键查询与索引 | 代表性数据量、MySQL 8、`EXPLAIN ANALYZE` | 查询计划、扫描行、延迟、慢查询记录 | 无真库与数据集 |
| D-BE-04 | Redis 限流和故障边界 | Redis、真实限流 key、可控断连 | 正常限流、超限响应、断连降级和恢复证据 | Redis 未配置 |
| D-BE-05 | Kafka 重复、乱序、重试与 inbox 幂等 | Kafka、消费者组、重复/乱序事件、积压观测 | 单次业务效果、重试、DLQ/积压、恢复证据 | Kafka 未配置 |
| D-BE-06 | Elasticsearch 正常与降级路径 | Elasticsearch、索引、失败注入 | 正常查询、fallback、重建/重试和恢复证据 | Elasticsearch 未配置 |
| D-BE-07 | V17 引用 API 多状态 | 无引用、全 ACTIVE、ACTIVE+BROKEN、公开转私/下线帖子 | HTTP 请求/响应、角色、数据 ID、状态字段一致 | 无后端服务、数据库和测试账号 |
| D-BE-08 | 多角色 HTTP 权限 | anonymous/member/moderator/question operator/OPS | 每个角色的允许/拒绝矩阵和稳定错误码 | 无 acceptance 环境和账号 |
| D-BE-09 | strict readiness | 完整依赖、OPS 角色、可构造队列积压 | healthy 时 200；依赖禁用、失败或关键积压时 503 | 无完整依赖栈 |
| D-BE-10 | V19 跨频道策展 | admin/member、跨频道/单频道专题、未知域帖子 | scope 阻断、发布/下线、公开读取结果 | 历史文档称可跑，但当前无可复核环境证据 |
| D-BE-11 | V20 阅读脉络与成长路径 | A→B→C 关系、私密切换、PENDING 关系、真实成长账号 | 仅 APPROVED+VISIBLE、截断、本人数据一致 | 历史矩阵已挂账，服务器环境未恢复证明缺失 |
| D-BE-12 | 备份、恢复和回滚演练 | 可恢复数据集、已校验备份、部署产物 | RTO/RPO、恢复校验、回滚后健康与数据一致性 | 未安排演练窗口 |

动态执行还必须记录：

- 环境标识、配置版本和执行时间。
- MySQL、Redis、Kafka、Elasticsearch 的实际版本。
- 后端 SHA、前端 SHA 和已执行 migration。
- 测试账号角色、数据 ID 和清理方式。
- 正常路径、失败路径、恢复路径。
- 原始日志或截图的受控存放位置。

任一发布前必测动态项保持 `BLOCKED` 时，整体状态不得提升为 `RELEASABLE`。

## 10. `NVD_API_KEY` 风险

当前 security workflow 将 `secrets.NVD_API_KEY` 注入环境，并仅在 Secret 非空时向 Maven 增加 `-DnvdApiKey`。仓库内容和公开 run 元数据不能证明 Secret 已配置，也不能证明它在历史成功运行中被使用。

主要风险：

| 风险 | 影响 | 当前控制 | 仍需动作 |
|---|---|---|---|
| Secret 缺失或为空 | NVD 无 Key 限流，冷启动可能显著变慢或超时 | 日级缓存、180 分钟 timeout | 管理员只确认“已配置/未配置”，不得暴露值 |
| Key 无效、过期或被限流 | Dependency-Check 更新失败，最终 job 失败 | `failOnError=true`，失败由 enforcement step 阻断 | 记录失败 run、轮换 Key、重跑 |
| 只有文档变化不触发 PR 审计 | 最终 V21 SHA 没有直接审计 run | `workflow_dispatch` | 手动对最终 ref 执行，或签署等价依赖基线豁免 |
| 缓存过旧或不完整 | 扫描耗时或数据新鲜度不足 | 每日 cache key，允许旧缓存续传 | 记录 cache hit、审计完成时间和报告生成时间 |
| 把 Key 写入证据 | Secret 泄露 | GitHub Secret masking | 本文只记录 presence、owner 和轮换日期 |

主 Agent 占位：

| 字段 | 填写 |
|---|---|
| Secret presence 由仓库管理员确认 | `UNKNOWN`，仓库和公开 run 元数据不能证明 Secret 状态 |
| 确认时间与责任人 | `PENDING_REPOSITORY_ADMIN_CONFIRMATION` |
| 最终 SHA audit run | 等价生产 SHA run `30296061545`，结果 success |
| cache hit | `TRUE`，成功 attempt 2 恢复了 attempt 1 保存的 NVD 缓存 |
| 报告生成时间 | workflow 完成于 `2026-07-28T06:29:34+08:00` |
| 若使用等价基线豁免 | `ACCEPTED`；主 Agent；2026-07-28；仅对 docs-only diff 有效；任何生产/POM/workflow/script/migration 变化立即失效 |

没有成功审计或明确、临时、可追踪的豁免时，不得发布。

## 11. 回滚说明

### 11.1 本 V21 后端变更

本任务只有发布证据文档，没有生产代码、依赖、配置、API 或数据库变化：

- 未合并时，可删除未合并的 V21 分支或放弃该文档提交，不影响 PR #17。
- 已合并时，使用普通 `git revert <包含本文的 evidence commit SHA>` 回退文档提交。
- 禁止 force push 或改写冻结的 `dev-v2` 历史。
- 不需要重新部署后端 JAR。
- 不需要数据库 rollback，不删除或迁移数据。
- 不存在 API 版本兼容回滚。

### 11.2 未来随 `dev-v2` 发布时

如果后续发布包仍确认 V21 后端只有文档变化：

- 生产后端继续使用既定、已校验的应用 artifact，不应仅因本文产生新的运行时部署。
- 前端 V17 修复按前端独立提交和静态资源版本回滚。
- 既有 `dev-v2` migration 和生产代码的回滚属于其原版本范围，不得伪装成 V21 零迁移回滚。
- 发生运行时问题时，按实际部署 SHA、artifact checksum、数据库备份和版本台账执行回滚。

如果最终 V21 差异出现任何生产文件，本节不再适用，必须补充部署顺序、兼容窗口、数据恢复和回滚演练。

## 12. 最终签署

| 角色 | 结论/签名 |
|---|---|
| 后端证据整理 | 本文已建立；Maven 与一次性静态检查已执行；未启动服务、数据库、容器或浏览器 |
| 主 Agent 静态验证 | `Codex / 2026-07-28 / LOCAL_STATIC_VERIFIED` |
| Dependency Audit 复核 | `Codex / 2026-07-28 / 生产 SHA 等价基线豁免` |
| 独立 QA | `James / 2026-07-28 / PASS_WITH_BLOCKERS / 无 P0` |
| 发布决策人 | `Codex / 2026-07-28 / PASS_WITH_BLOCKERS` |

最终允许的当前阶段结论：

```text
STATIC_VERIFIED + DYNAMIC_BLOCKED = PASS_WITH_BLOCKERS
```

只有 R1-R16 完成、所有发布前动态必测项通过、回滚可执行、待发布 SHA 与 CI/SBOM/artifact checksum 完全一致时，才允许把状态改为 `RELEASABLE`。

## 13. 证据来源

- V21 跨仓库方案：`C:\vibe-coding\offerlab\文档\V21\OfferLab-V21-版本基线与可发布性收口详细方案-2026-07-28.md`
- 根 POM：`pom.xml`
- Backend CI：`.github/workflows/ci.yml`
- Dependency Audit：`.github/workflows/security.yml`
- CI 门禁说明：`docs/ci-quality-gates.md`
- 动态验收边界：`docs/acceptance-runbook.md`
- Migration 说明：`db/migration/README.md`
- Migration manifest：`db/migration/flyway-manifest.json`
- 本轮本地报告：`*/target/surefire-reports/TEST-*.xml`
- 冻结基线 CI：Runs `30296061502`、`30296058314`
- 冻结基线依赖审计：Run `30296061545`
