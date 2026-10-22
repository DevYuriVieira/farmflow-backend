ALTER TABLE machinery ALTER COLUMN next_maintenance_date TYPE DATE USING (
    CASE 
        WHEN next_maintenance_date IS NOT NULL AND next_maintenance_date <> '' 
        THEN next_maintenance_date::DATE 
        ELSE NULL 
    END
);
