-- 支付后把临时 Redis 座位锁转换为持久已售标记，不返还库存。
-- KEYS[1] reservation, KEYS[2..] seats
-- ARGV: userId, requestId, orderNo

local owner = ARGV[1] .. '|' .. ARGV[2]
for i = 2, #KEYS do
  local current = redis.call('GET', KEYS[i])
  if current == owner or current == 'SOLD|' .. ARGV[3] then
    redis.call('SET', KEYS[i], 'SOLD|' .. ARGV[3])
  end
end
redis.call('DEL', KEYS[1])
return 1
