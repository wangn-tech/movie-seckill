#!/usr/bin/env bash
set -Eeuo pipefail

mysql_result="$(docker exec -i maoyan-mysql sh -c 'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' <<'SQL'
SELECT CONCAT('negative_stock=', COUNT(*)) FROM movie_schedule WHERE id=9001 AND available_seats < 0;
SELECT CONCAT('duplicate_request=', COUNT(*)) FROM (
  SELECT lock_token FROM ticket_order WHERE schedule_id=9001 GROUP BY lock_token HAVING COUNT(*) > 1
) duplicated_requests;
SELECT CONCAT('duplicate_active_seat=', COUNT(*)) FROM (
  SELECT os.row_num, os.col_num
  FROM order_seat os JOIN ticket_order o ON o.id=os.order_id
  WHERE o.schedule_id=9001 AND o.status IN (0,1)
  GROUP BY os.row_num, os.col_num HAVING COUNT(*) > 1
) duplicated_seats;
SELECT CONCAT('mysql_stock=', available_seats) FROM movie_schedule WHERE id=9001;
SELECT CONCAT('outbox_unsettled=', COUNT(*)) FROM outbox_event
WHERE schedule_id=9001 AND (status IN ('PENDING','DEAD') OR process_status <> 'SUCCEEDED');
SQL
)"

redis_stock="$(docker exec maoyan-redis redis-cli --raw GET seckill:stock:9001)"
echo "$mysql_result"
echo "redis_stock=$redis_stock"

negative="$(awk -F= '/negative_stock/{print $2}' <<<"$mysql_result")"
duplicate_request="$(awk -F= '/duplicate_request/{print $2}' <<<"$mysql_result")"
duplicate_seat="$(awk -F= '/duplicate_active_seat/{print $2}' <<<"$mysql_result")"
mysql_stock="$(awk -F= '/mysql_stock/{print $2}' <<<"$mysql_result")"
unsettled="$(awk -F= '/outbox_unsettled/{print $2}' <<<"$mysql_result")"

if [[ "$negative" != "0" || "$duplicate_request" != "0" || "$duplicate_seat" != "0" \
   || "$unsettled" != "0" || "$mysql_stock" != "$redis_stock" ]]; then
  echo "压测后数据不变量校验失败。" >&2
  exit 1
fi
echo "压测后数据不变量校验通过。"
