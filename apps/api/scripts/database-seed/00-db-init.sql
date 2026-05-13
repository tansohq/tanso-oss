create database core_db;


-- Create a user (if not already created)
CREATE USER test WITH ENCRYPTED PASSWORD 'password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE core_db TO test;