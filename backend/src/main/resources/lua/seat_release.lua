-- =====================================================
-- 关单/取消时返还库存和座位锁
-- 只有 reservation 凭证还在且 owner 匹配时才返还，重复补偿不会重复加库存
--
-- KEYS[1] = stock:{scheduleId}
-- KEYS[2] = reservation:{requestId}
-- KEYS[3..] = seat:{scheduleId}:{row}-{col}
-- ARGV[1] = userId
-- ARGV[2] = requestId
-- ARGV[3] = seatCount
-- 返回: 实际返还的库存数
-- =====================================================

local stock_key = KEYS[1]
local reservation_key = KEYS[2]
local user_id = ARGV[1]
local seat_count = tonumber(ARGV[3])

local reservation = redis.call('GET', reservation_key)
if not reservation then
  return 0  -- 凭证已过期/已用，不重复返还
end

-- 校验 owner
local sep = string.find(reservation, '|')
if string.sub(reservation, 1, sep - 1) ~= user_id then
  return 0
end

-- 删除座位锁
for i = 3, #KEYS do
  if redis.call('GET', KEYS[i]) == user_id .. '|' .. ARGV[2] then
    redis.call('DEL', KEYS[i])
  end
end

-- 删除一次性凭证（防止重复返还）
redis.call('DEL', reservation_key)

-- 返还库存
redis.call('INCRBY', stock_key, seat_count)
return seat_count
