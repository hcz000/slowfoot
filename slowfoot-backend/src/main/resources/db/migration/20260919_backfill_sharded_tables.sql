-- Run after 20260919_create_sharded_tables.sql while application writes are stopped.
-- The script is rerunnable and leaves all legacy logical tables untouched for rollback.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `assert_sharding_keys`;
DELIMITER //
CREATE PROCEDURE `assert_sharding_keys`()
BEGIN
    IF EXISTS (SELECT 1 FROM `work_like` WHERE `is_deleted` = 0 AND `work_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'work_like contains a NULL work_id';
    END IF;
    IF EXISTS (SELECT 1 FROM `work_share` WHERE `is_deleted` = 0 AND `work_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'work_share contains a NULL work_id';
    END IF;
    IF EXISTS (SELECT 1 FROM `favorites_work` WHERE `user_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'favorites_work contains a NULL user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM `user_notification` WHERE `is_deleted` = 0 AND `user_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_notification contains a NULL user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM `work_view_history` WHERE `user_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'work_view_history contains a NULL user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM `user_search_history` WHERE `user_id` IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_search_history contains a NULL user_id';
    END IF;
END//
DELIMITER ;

CALL `assert_sharding_keys`();
DROP PROCEDURE `assert_sharding_keys`;

DROP PROCEDURE IF EXISTS `backfill_mod_4`;
DELIMITER //
CREATE PROCEDURE `backfill_mod_4`(
    IN logical_table VARCHAR(64),
    IN sharding_column VARCHAR(64),
    IN row_filter VARCHAR(255)
)
BEGIN
    DECLARE shard_index INT DEFAULT 0;
    WHILE shard_index < 4 DO
        SET @backfill_sql = CONCAT(
            'REPLACE INTO `', logical_table, '_', shard_index,
            '` SELECT * FROM `', logical_table,
            '` WHERE MOD(`', sharding_column, '`, 4) = ', shard_index,
            row_filter
        );
        PREPARE backfill_statement FROM @backfill_sql;
        EXECUTE backfill_statement;
        DEALLOCATE PREPARE backfill_statement;
        SET shard_index = shard_index + 1;
    END WHILE;
END//
DELIMITER ;

-- Logically deleted rows are intentionally omitted because they would retain unique keys.
CALL `backfill_mod_4`('work_like', 'work_id', ' AND is_deleted = 0');
CALL `backfill_mod_4`('work_share', 'work_id', ' AND is_deleted = 0');
CALL `backfill_mod_4`('favorites_work', 'user_id', '');
CALL `backfill_mod_4`('user_notification', 'user_id', ' AND is_deleted = 0');
CALL `backfill_mod_4`('work_view_history', 'user_id', '');
CALL `backfill_mod_4`('user_search_history', 'user_id', '');

DROP PROCEDURE `backfill_mod_4`;
