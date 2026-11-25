# Consolidated database setup scripts

One script per dialect that creates the **whole current schema plus the seed data** in a single file, for a **new**
environment:

| File | Target |
|---|---|
| `postgresql/cr-qa-management-postgresql.sql` | PostgreSQL (the `postgres` / `prod` profile) |
| `oracle/cr-qa-management-oracle.sql` | Oracle (the `oracle` profile) |

They are **not** migrations and are not on Flyway's classpath: the application, `start.sh` and every existing
environment keep using `db/migration`, `db/postgresql` and `db/oracle` exactly as before. Run one of these only when
standing a database up from scratch.

## Use it

PostgreSQL:

```bash
createdb crqa
psql -d crqa -f postgresql/cr-qa-management-postgresql.sql
```

Oracle (as the schema owner):

```bash
sqlplus mytelco/password@//host:1524/SERVICE @oracle/cr-qa-management-oracle.sql
```

Then start the application **once** with the baseline switch, so Flyway does not replay the migrations on top of the
schema you just created:

```
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=27    # 26 for Oracle
```

or in `application.yml`:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 27      # 26 for Oracle
```

Flyway then records the schema as already being at that version and applies only **future** migrations — which is what
makes a database created from these scripts stay upgradeable. Leaving the setting on is harmless: baselining only
happens when a non-empty schema has no history table yet.

## What is inside, and how each was produced

* **PostgreSQL** — generated with `pg_dump` from a database built by the migration chain itself (`db/postgresql`,
  V1..V27), so it is that chain's own result: 27 tables, 14 sequences, indexes, constraints, a `setval` per sequence
  and the 50 seed rows. It was verified by running it against an empty database, diffing schema **and** data dumps
  against the migration-built one (identical), then starting the application on it with Flyway disabled and inserting
  rows — the sequence-backed ids work.
* **Oracle** — the Oracle migration chain (V1..V26) concatenated verbatim, in version order, so every statement is one
  Flyway has already run on Oracle. Tables are created with their pre-`IT_` names and renamed by V13, exactly as the
  chain does. It has **not** been executed against a live Oracle in this repository, so run it on a scratch schema
  first and compare with a migration-built one before using it in anger.

## The one fix both scripts carry

The chain's seed inserts call the `IT_*` sequences, but the chain only creates them later (PostgreSQL V15; Oracle
V14/V16/V17), so **a fresh database cannot finish the migration chain** — those inserts fail with
`relation "it_users_seq" does not exist`. Both scripts therefore create every sequence **before** the first insert.
Case differs per dialect and is deliberate: PostgreSQL folds unquoted names down (`it_users_seq`), Oracle folds them up
(`IT_USERS_SEQ`) — which is what both the seed inserts and Hibernate's
`@SequenceGenerator(sequenceName = "IT_USERS_SEQ")` resolve to. Do not "tidy" the case.

## Regenerating after new migrations

Regenerate rather than hand-editing: build a scratch database with the migration chain, dump it (PostgreSQL), or
concatenate the chain files (Oracle) — then **diff the result against the migration-built database** before shipping
it. The important part of that recipe is the diff; the rest is bookkeeping.

## Development is unchanged

H2/dev still applies `db/migration` with Flyway on first start and `DevDataInitializer` seeds the users. These scripts
exist for real environments.
