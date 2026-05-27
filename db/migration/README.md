# OfferLab Database Migrations

This folder contains non-destructive SQL for existing databases. `db/init/*`
is used for fresh local initialization only.

Run migration scripts manually after reviewing them. The current script creates
missing governance tables and adds missing indexes only when absent:

```sql
SOURCE db/migration/20260524_ops_governance.sql;
```

Do not run schema reset or table drop commands against production data.
