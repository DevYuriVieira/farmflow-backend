CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    phone VARCHAR(50),
    farm_id VARCHAR(36),
    avatar_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS farms (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    total_area_hectares DOUBLE PRECISION NOT NULL,
    primary_crop VARCHAR(100) NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    status VARCHAR(50) DEFAULT 'active'
);

CREATE TABLE IF NOT EXISTS plantations (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    crop VARCHAR(100) NOT NULL,
    variety VARCHAR(100),
    area_hectares DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) DEFAULT 'planted',
    planting_date VARCHAR(50),
    harvest_estimate_date VARCHAR(50),
    expected_yield_tons DOUBLE PRECISION,
    notes VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS activities (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    plantation_id VARCHAR(36) NOT NULL,
    farm_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(100) NOT NULL,
    date VARCHAR(50) NOT NULL,
    completed BOOLEAN DEFAULT FALSE,
    priority VARCHAR(50) DEFAULT 'medium',
    notes VARCHAR(1000),
    cost_estimate DOUBLE PRECISION,
    completed_at VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS inventory_items (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit VARCHAR(50) NOT NULL,
    min_threshold DOUBLE PRECISION NOT NULL,
    unit_price DOUBLE PRECISION,
    location VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS machinery (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    year INTEGER,
    status VARCHAR(50) DEFAULT 'operational',
    hours_used DOUBLE PRECISION DEFAULT 0.0,
    last_maintenance_date VARCHAR(50),
    next_maintenance_date VARCHAR(50),
    fuel_level_percent DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS pest_records (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    plantation_id VARCHAR(36) NOT NULL,
    pest_name VARCHAR(255) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    confidence_score DOUBLE PRECISION,
    photo_url VARCHAR(500),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    recommended_action VARCHAR(1000),
    detected_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS telemetry_readings (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    plantation_id VARCHAR(36),
    sensor_id VARCHAR(100) NOT NULL,
    reading_type VARCHAR(50) NOT NULL,
    reading_value DOUBLE PRECISION NOT NULL,
    unit_measure VARCHAR(50) NOT NULL,
    battery_level VARCHAR(50),
    recorded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_plantations_farm_id ON plantations(farm_id);
CREATE INDEX IF NOT EXISTS idx_activities_farm_id ON activities(farm_id);
CREATE INDEX IF NOT EXISTS idx_activities_plantation_id ON activities(plantation_id);
CREATE INDEX IF NOT EXISTS idx_inventory_farm_id ON inventory_items(farm_id);
CREATE INDEX IF NOT EXISTS idx_machinery_farm_id ON machinery(farm_id);
CREATE INDEX IF NOT EXISTS idx_pest_records_farm_id ON pest_records(farm_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_farm_sensor ON telemetry_readings(farm_id, sensor_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_recorded_at ON telemetry_readings(recorded_at);
