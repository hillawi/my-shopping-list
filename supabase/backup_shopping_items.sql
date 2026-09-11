-- Households migration — PRE-migration safety snapshot of shopping_items.
--
-- Run this BEFORE production_migration.sql. It doesn't change anything the
-- migration itself would touch (the migration never deletes or updates existing
-- shopping_items rows — it only adds a nullable household_id column), but it
-- gives you a stable, named copy of the pre-migration data to restore from with
-- restore_shopping_items.sql once a household exists to assign it to.
--
-- No IF NOT EXISTS here on purpose: if this errors with "relation
-- shopping_items_backup already exists", a backup already exists from a
-- previous run — drop it first only if you're sure you want to replace it
-- (`drop table shopping_items_backup;`), otherwise you'd silently keep an
-- older/incomplete snapshot without noticing.

create table shopping_items_backup as
select * from shopping_items;

-- Sanity check: row counts should match.
select
  (select count(*) from shopping_items) as live_count,
  (select count(*) from shopping_items_backup) as backup_count;
