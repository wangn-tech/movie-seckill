-- 幂等释放库存与座位。数据库取消事件是释放事实来源，即使应用重启后 reservation 丢失也可执行。
-- KEYS[1] stock, KEYS[2] reservation, KEYS[3] release marker, KEYS[4..] seats
-- ARGV: userId, requestId, seatCount

if redis.call('EXISTS', KEYS[3]) == 1 then
  return 0
end

local owner = ARGV[1] .. '|' .. ARGV[2]
for i = 4, #KEYS do
  if redis.call('GET', KEYS[i]) == owner then
    redis.call('DEL', KEYS[i])
  end
end

redis.call('DEL', KEYS[2])
redis.call('INCRBY', KEYS[1], tonumber(ARGV[3]))
redis.call('SET', KEYS[3], '1', 'EX', 604800)
return tonumber(ARGV[3])
