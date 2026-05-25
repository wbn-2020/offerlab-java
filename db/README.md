# OfferLab database scripts

Use the folders differently:

- `db/migration`: additive scripts for existing databases. Review and run these
  manually in the target environment.
- `db/init`: fresh local initialization scripts. These files may contain
  `DROP TABLE IF EXISTS` and must not be run against an existing database unless
  you intentionally want to rebuild it from scratch.

Before running an existing-database migration, use:

```powershell
.\scripts\check-migration-safety.ps1
```

The check is intentionally conservative. It scans `db/migration` for destructive
DDL/DML patterns and fails fast when it sees statements such as `DROP TABLE`,
`TRUNCATE`, or broad `DELETE`/`UPDATE` statements without an obvious `WHERE`.
