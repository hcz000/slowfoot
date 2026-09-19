-- Every row must have exactly one target shard and zero routing mismatches before cutover.

SELECT
    `logical_table`,
    `source_rows`,
    `sharded_rows`,
    `source_rows` - `sharded_rows` AS `row_delta`
FROM (
    SELECT 'work_like' AS `logical_table`,
           (SELECT COUNT(*) FROM `work_like` WHERE `is_deleted` = 0) AS `source_rows`,
           ((SELECT COUNT(*) FROM `work_like_0`) + (SELECT COUNT(*) FROM `work_like_1`)
            + (SELECT COUNT(*) FROM `work_like_2`) + (SELECT COUNT(*) FROM `work_like_3`)) AS `sharded_rows`
    UNION ALL
    SELECT 'work_share',
           (SELECT COUNT(*) FROM `work_share` WHERE `is_deleted` = 0),
           ((SELECT COUNT(*) FROM `work_share_0`) + (SELECT COUNT(*) FROM `work_share_1`)
            + (SELECT COUNT(*) FROM `work_share_2`) + (SELECT COUNT(*) FROM `work_share_3`))
    UNION ALL
    SELECT 'favorites_work',
           (SELECT COUNT(*) FROM `favorites_work`),
           ((SELECT COUNT(*) FROM `favorites_work_0`) + (SELECT COUNT(*) FROM `favorites_work_1`)
            + (SELECT COUNT(*) FROM `favorites_work_2`) + (SELECT COUNT(*) FROM `favorites_work_3`))
    UNION ALL
    SELECT 'user_notification',
           (SELECT COUNT(*) FROM `user_notification` WHERE `is_deleted` = 0),
           ((SELECT COUNT(*) FROM `user_notification_0`) + (SELECT COUNT(*) FROM `user_notification_1`)
            + (SELECT COUNT(*) FROM `user_notification_2`) + (SELECT COUNT(*) FROM `user_notification_3`))
    UNION ALL
    SELECT 'work_view_history',
           (SELECT COUNT(*) FROM `work_view_history`),
           ((SELECT COUNT(*) FROM `work_view_history_0`) + (SELECT COUNT(*) FROM `work_view_history_1`)
            + (SELECT COUNT(*) FROM `work_view_history_2`) + (SELECT COUNT(*) FROM `work_view_history_3`))
    UNION ALL
    SELECT 'user_search_history',
           (SELECT COUNT(*) FROM `user_search_history`),
           ((SELECT COUNT(*) FROM `user_search_history_0`) + (SELECT COUNT(*) FROM `user_search_history_1`)
            + (SELECT COUNT(*) FROM `user_search_history_2`) + (SELECT COUNT(*) FROM `user_search_history_3`))
) AS `counts`;

SELECT 'work_like' AS `logical_table`,
       ((SELECT COUNT(*) FROM `work_like_0` WHERE MOD(`work_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `work_like_1` WHERE MOD(`work_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `work_like_2` WHERE MOD(`work_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `work_like_3` WHERE MOD(`work_id`, 4) <> 3)) AS `misrouted_rows`
UNION ALL
SELECT 'work_share',
       ((SELECT COUNT(*) FROM `work_share_0` WHERE MOD(`work_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `work_share_1` WHERE MOD(`work_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `work_share_2` WHERE MOD(`work_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `work_share_3` WHERE MOD(`work_id`, 4) <> 3))
UNION ALL
SELECT 'favorites_work',
       ((SELECT COUNT(*) FROM `favorites_work_0` WHERE MOD(`user_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `favorites_work_1` WHERE MOD(`user_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `favorites_work_2` WHERE MOD(`user_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `favorites_work_3` WHERE MOD(`user_id`, 4) <> 3))
UNION ALL
SELECT 'user_notification',
       ((SELECT COUNT(*) FROM `user_notification_0` WHERE MOD(`user_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `user_notification_1` WHERE MOD(`user_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `user_notification_2` WHERE MOD(`user_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `user_notification_3` WHERE MOD(`user_id`, 4) <> 3))
UNION ALL
SELECT 'work_view_history',
       ((SELECT COUNT(*) FROM `work_view_history_0` WHERE MOD(`user_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `work_view_history_1` WHERE MOD(`user_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `work_view_history_2` WHERE MOD(`user_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `work_view_history_3` WHERE MOD(`user_id`, 4) <> 3))
UNION ALL
SELECT 'user_search_history',
       ((SELECT COUNT(*) FROM `user_search_history_0` WHERE MOD(`user_id`, 4) <> 0)
        + (SELECT COUNT(*) FROM `user_search_history_1` WHERE MOD(`user_id`, 4) <> 1)
        + (SELECT COUNT(*) FROM `user_search_history_2` WHERE MOD(`user_id`, 4) <> 2)
        + (SELECT COUNT(*) FROM `user_search_history_3` WHERE MOD(`user_id`, 4) <> 3));
