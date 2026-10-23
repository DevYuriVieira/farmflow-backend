-- V9: Align legacy database schema columns and table names with JPA entity mappings
ALTER TABLE machinery RENAME COLUMN hours_used TO operating_hours;

ALTER TABLE inventory_items RENAME COLUMN unit TO unit_measure;
ALTER TABLE inventory_items RENAME COLUMN min_threshold TO minimum_threshold;
ALTER TABLE inventory_items ALTER COLUMN minimum_threshold DROP NOT NULL;

ALTER TABLE pest_records RENAME TO pest_scouting_records;

ALTER TABLE processed_messages ALTER COLUMN message_id TYPE VARCHAR(100);
