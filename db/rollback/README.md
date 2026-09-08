# db/rollback — 手动回滚脚本（不进入 Flyway / 同步管线）

本目录存放与某个已合并迁移一一对应的**手动回滚脚本**。

约定：

1. 文件名与规范迁移同名：`<YYYYMMDD>_<description>_rollback.sql`。
2. 只能手动执行；执行前必须先回滚写入这些字段的引用该迁移的应用版本。
3. `db/migration/` 与 `sync-flyway-resources.ps1` 不会读取本目录；`20*.sql` 过滤器只作用于 `db/migration/`。
4. 回滚脚本同样不进入 `db/init/` 空库重建镜像（空库天然没有需要回滚的对象）。

当前脚本：

- `20260901_registration_consent_rollback.sql`：回滚 `20260901_registration_consent.sql`
  为 `t_user_account` 增加的 `terms_accepted_at/terms_version/privacy_version` 三列。
