-- Creates the three schemas used by the waitlist platform.
-- This script runs once when the Postgres container is first initialised.
CREATE SCHEMA IF NOT EXISTS ingestion;
CREATE SCHEMA IF NOT EXISTS admin;
CREATE SCHEMA IF NOT EXISTS notification;
