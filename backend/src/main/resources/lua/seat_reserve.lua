-- =====================================================
-- 抢座核心 Lua 脚本（面试重点）
-- 一次脚本原子完成：requestId 幂等 + 座位冲突检查 + 库存预扣 + 座位锁定
--
-- KEYS[1] = seckill:stock:{scheduleId}        场次库存
-- KEYS[2] = seckill:reservation:{requestId}   一次性幂等凭证
-- KEYS[3..] = seckill:seat:{scheduleId}:{row}-{col}  所选座位锁
--
-- ARGV[1] = userId
-- ARGV[2] = requestId
-- ARGV[3] = seatLockTtl   座位锁 TTL（秒）
-- ARGV[4] = reservationTtl 凭证 TTL（秒）
-- ARGV[5] = seatCount     选座数量
--
-- 返回:  1 = 成功
--        2 = 幂等成功（同一 requestId 重复请求）
--       -1 = 座位被他人占用
--       -2 = 库存不足
-- =====================================================

local stock_key = KEYS[1]
local reservation_key = KEYS[2]

local user_id   = ARGV[1]
local request_id = ARGV[2]
local lock_ttl  = tonumber(ARGV[3])
local resv_ttl  = tonumber(ARGV[4])
local seat_count = tonumber(ARGV[5])

-- ① 幂等：同一 requestId 已成功过，直接返回
if redis.call('EXISTS', reservation_key) == 1 then
  return 2
end

-- ② 座位冲突检查：任一座位被他人持有即失败
for i = 3, #KEYS do
  local owner = redis.call('GET', KEYS[i])
  if owner then
    -- owner 格式 "userId|requestId"，不是本人则冲突
    local sep = string.find(owner, '|')
    local owner_user = string.sub(owner, 1, sep - 1)
    if owner_user ~= user_id then
      return -1
    end
    -- 是本人重复选了同座，幂等处理
  end
end

-- ③ 库存校验
local stock = tonumber(redis.call('GET', stock_key) or '0')
if stock < seat_count then
  return -2
end

-- ④ 原子预扣库存
redis.call('DECRBY', stock_key, seat_count)

-- ⑤ 锁定所选座位
for i = 3, #KEYS do
  redis.call('SET', KEYS[i], user_id .. '|' .. request_id, 'EX', lock_ttl)
end

-- ⑥ 写入一次性凭证（关单时用它判断是否真的返还过库存）
redis.call('SET', reservation_key, user_id .. '|' .. seat_count, 'EX', resv_ttl)

return 1
