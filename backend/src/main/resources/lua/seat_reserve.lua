-- 原子抢座：精确幂等、座位冲突检查、库存预扣、批量锁座。
-- KEYS[1] stock, KEYS[2] reservation, KEYS[3..] seats
-- ARGV: userId, requestId, fingerprint, lockTtl, reservationTtl, seatCount
-- 1=成功, 2=精确幂等, -1=座位冲突, -2=库存不足, -3=requestId内容冲突

local stock_key = KEYS[1]
local reservation_key = KEYS[2]
local owner = ARGV[1] .. '|' .. ARGV[2]
local reservation_value = owner .. '|' .. ARGV[3] .. '|' .. ARGV[6]

local existing = redis.call('GET', reservation_key)
if existing then
  if existing == reservation_value then
    return 2
  end
  return -3
end

for i = 3, #KEYS do
  local seat_owner = redis.call('GET', KEYS[i])
  if seat_owner and seat_owner ~= owner then
    return -1
  end
end

local stock = tonumber(redis.call('GET', stock_key) or '0')
local seat_count = tonumber(ARGV[6])
if stock < seat_count then
  return -2
end

redis.call('DECRBY', stock_key, seat_count)
for i = 3, #KEYS do
  redis.call('SET', KEYS[i], owner, 'EX', tonumber(ARGV[4]))
end
redis.call('SET', reservation_key, reservation_value, 'EX', tonumber(ARGV[5]))
return 1
