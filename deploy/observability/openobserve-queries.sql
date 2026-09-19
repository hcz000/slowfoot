-- Run one query at a time in OpenObserve. Change "slowfoot" if OPENOBSERVE_STREAM differs.

-- HTTP requests by status over time (line or stacked bar panel).
SELECT histogram(_timestamp) AS time_bucket,
       http_status,
       COUNT(*) AS request_count
FROM "slowfoot"
WHERE event_category = 'http'
GROUP BY time_bucket, http_status
ORDER BY time_bucket;

-- Average and maximum request duration by route (table panel).
SELECT http_method,
       url_path,
       COUNT(*) AS request_count,
       AVG(duration_ms) AS average_duration_ms,
       MAX(duration_ms) AS maximum_duration_ms
FROM "slowfoot"
WHERE event_category = 'http'
GROUP BY http_method, url_path
ORDER BY maximum_duration_ms DESC
LIMIT 20;

-- Application warnings and errors over time (stacked bar panel).
SELECT histogram(_timestamp) AS time_bucket,
       log_level,
       COUNT(*) AS event_count
FROM "slowfoot"
WHERE log_level IN ('WARN', 'ERROR')
GROUP BY time_bucket, log_level
ORDER BY time_bucket;

-- Security events by action and outcome (bar or table panel).
SELECT event_action,
       event_outcome,
       COUNT(*) AS event_count
FROM "slowfoot"
WHERE event_category IN ('authentication', 'authorization', 'account', 'security')
GROUP BY event_action, event_outcome
ORDER BY event_count DESC;

-- Repeated login failures by source address (table panel and alert query).
SELECT source_ip,
       COUNT(*) AS failure_count
FROM "slowfoot"
WHERE event_action = 'login'
  AND event_outcome = 'failure'
GROUP BY source_ip
ORDER BY failure_count DESC;

-- Unhandled exceptions with correlation IDs (table panel).
SELECT _timestamp,
       request_id,
       error_type,
       message,
       error_stack_trace
FROM "slowfoot"
WHERE log_level = 'ERROR'
ORDER BY _timestamp DESC
LIMIT 100;

-- Parameter-safe slow SQL emitted by P6Spy (table panel).
SELECT _timestamp,
       request_id,
       message
FROM "slowfoot"
WHERE message LIKE 'sql_duration_ms=%'
ORDER BY _timestamp DESC
LIMIT 100;
