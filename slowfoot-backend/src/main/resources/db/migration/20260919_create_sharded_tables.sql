-- Creates the four physical tables for each sharded logical table.
-- This script does not copy or delete legacy data. Run the backfill script separately.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `work_like_0` LIKE `work_like`;
CREATE TABLE IF NOT EXISTS `work_like_1` LIKE `work_like`;
CREATE TABLE IF NOT EXISTS `work_like_2` LIKE `work_like`;
CREATE TABLE IF NOT EXISTS `work_like_3` LIKE `work_like`;

CREATE TABLE IF NOT EXISTS `work_share_0` LIKE `work_share`;
CREATE TABLE IF NOT EXISTS `work_share_1` LIKE `work_share`;
CREATE TABLE IF NOT EXISTS `work_share_2` LIKE `work_share`;
CREATE TABLE IF NOT EXISTS `work_share_3` LIKE `work_share`;

CREATE TABLE IF NOT EXISTS `favorites_work_0` LIKE `favorites_work`;
CREATE TABLE IF NOT EXISTS `favorites_work_1` LIKE `favorites_work`;
CREATE TABLE IF NOT EXISTS `favorites_work_2` LIKE `favorites_work`;
CREATE TABLE IF NOT EXISTS `favorites_work_3` LIKE `favorites_work`;

CREATE TABLE IF NOT EXISTS `work_view_history_0` LIKE `work_view_history`;
CREATE TABLE IF NOT EXISTS `work_view_history_1` LIKE `work_view_history`;
CREATE TABLE IF NOT EXISTS `work_view_history_2` LIKE `work_view_history`;
CREATE TABLE IF NOT EXISTS `work_view_history_3` LIKE `work_view_history`;

CREATE TABLE IF NOT EXISTS `user_search_history_0` LIKE `user_search_history`;
CREATE TABLE IF NOT EXISTS `user_search_history_1` LIKE `user_search_history`;
CREATE TABLE IF NOT EXISTS `user_search_history_2` LIKE `user_search_history`;
CREATE TABLE IF NOT EXISTS `user_search_history_3` LIKE `user_search_history`;

CREATE TABLE IF NOT EXISTS `user_notification_0` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    `gmt_created` DATETIME     NOT NULL,
    `gmt_updated` DATETIME     NOT NULL,
    `event_id`    VARCHAR(36)  NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `actor_id`    BIGINT       NULL,
    `type`        VARCHAR(32)  NOT NULL,
    `target_id`   BIGINT       NULL,
    `work_id`     BIGINT       NULL,
    `content`     VARCHAR(500) NOT NULL,
    `read_state`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_notification_event` (`user_id`, `event_id`),
    KEY `idx_user_notification_user_read` (`user_id`, `read_state`, `gmt_created`),
    KEY `idx_user_notification_target` (`target_id`, `type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知';
CREATE TABLE IF NOT EXISTS `user_notification_1` LIKE `user_notification_0`;
CREATE TABLE IF NOT EXISTS `user_notification_2` LIKE `user_notification_0`;
CREATE TABLE IF NOT EXISTS `user_notification_3` LIKE `user_notification_0`;

SET FOREIGN_KEY_CHECKS = 1;
