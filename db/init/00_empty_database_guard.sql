-- Guard for destructive init scripts.
-- Run init scripts only against an empty target schema. This file intentionally
-- aborts before the following DROP TABLE based init scripts when existing
-- tables are found in the current database.

DELIMITER $$

DROP PROCEDURE IF EXISTS offerlab_empty_database_guard $$
CREATE PROCEDURE offerlab_empty_database_guard()
BEGIN
    DECLARE existing_tables INT DEFAULT 0;

    SELECT COUNT(*)
      INTO existing_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_type = 'BASE TABLE';

    IF existing_tables > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OfferLab init SQL is destructive and must only run against an empty database.';
    END IF;
END $$

CALL offerlab_empty_database_guard() $$
DROP PROCEDURE IF EXISTS offerlab_empty_database_guard $$

DELIMITER ;
