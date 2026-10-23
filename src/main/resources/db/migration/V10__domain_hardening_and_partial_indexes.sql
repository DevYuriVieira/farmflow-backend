CREATE INDEX IF NOT EXISTS ix_farms_active
    ON farms (id)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS ix_plantations_active
    ON plantations (farm_id, status)
    WHERE status != 'harvested';

CREATE INDEX IF NOT EXISTS ix_activities_pending
    ON activities (farm_id, date)
    WHERE completed = false;

CREATE INDEX IF NOT EXISTS ix_machinery_active
    ON machinery (farm_id, status)
    WHERE status != 'decommissioned';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_farms_total_area_positive'
    ) THEN
        ALTER TABLE farms
            ADD CONSTRAINT chk_farms_total_area_positive CHECK (total_area_hectares > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_plantations_area_positive'
    ) THEN
        ALTER TABLE plantations
            ADD CONSTRAINT chk_plantations_area_positive CHECK (area_hectares > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_quantity_non_negative'
    ) THEN
        ALTER TABLE inventory_items
            ADD CONSTRAINT chk_inventory_quantity_non_negative CHECK (quantity >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_machinery_hours_non_negative'
    ) THEN
        ALTER TABLE machinery
            ADD CONSTRAINT chk_machinery_hours_non_negative CHECK (total_hours >= 0);
    END IF;
END $$;
