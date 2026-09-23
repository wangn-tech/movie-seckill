-- 令牌桶限流 Lua 脚本
-- KEYS[1] = rate:{userId}:{api}
-- ARGV[1] = rate  每秒补充令牌数
-- ARGV[2] = capacity 桶容量
-- ARGV[3] = now_ms 当前时间戳(毫秒)
-- 返回: 1=放行, 0=被限流

local key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local info = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(info[1])
local last = tonumber(info[2])

if tokens == nil then
  tokens = capacity
  last = now
end

-- 按时间差补充令牌
local elapsed = math.max(0, now - last)
tokens = math.min(capacity, tokens + elapsed / 1000 * rate)

if tokens >= 1 then
  tokens = tokens - 1
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('PEXPIRE', key, 60000)
  return 1
else
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('PEXPIRE', key, 60000)
  return 0
end
