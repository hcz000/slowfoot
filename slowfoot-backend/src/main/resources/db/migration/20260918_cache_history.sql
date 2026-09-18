-- Run once on an existing database. Fresh installations also receive these tables from db/schema.sql.
CREATE TABLE IF NOT EXISTS `work_view_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `work_id` BIGINT NOT NULL,
  `viewed_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_view_history_user_work` (`user_id`, `work_id`),
  KEY `idx_work_view_history_user_time` (`user_id`, `viewed_at`)
);

CREATE TABLE IF NOT EXISTS `user_search_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `keyword` VARCHAR(255) NOT NULL,
  `searched_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_search_history_user_keyword` (`user_id`, `keyword`),
  KEY `idx_user_search_history_user_time` (`user_id`, `searched_at`)
);
