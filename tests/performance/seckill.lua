-- wrk 压测脚本：抢座核心接口
-- 用法: wrk -t8 -c500 -d30s -s seckill.lua http://localhost:8080
-- 前置: 先跑 login.lua 拿到 token 写入 /tmp/wrk_token.txt

-- 读 token
local f = io.open("/tmp/wrk_token.txt", "r")
local token = f and f:read("*all") or "test-token"
if f then f:close() end

local scheduleId = os.getenv("SCHEDULE_ID") or "1"
local baseSeat = math.random(1, 8) * 10 + math.random(1, 10)

wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer " .. token

-- 计数器统计
local counter = 0

request = function()
  counter = counter + 1
  -- 每个请求选不同座位，模拟真实抢座
  local row = math.random(1, 8)
  local col = math.random(1, 10)
  -- requestId 唯一，保证幂等
  local requestId = string.format("perf_%d_%d", os.time(), counter)
  local body = string.format(
    '{"scheduleId":%s,"seats":[{"row":%d,"col":%d}],"requestId":"%s"}',
    scheduleId, row, col, requestId
  )
  wrk.body = body
  return wrk.format("POST", "/seckill/seize")
end

-- 响应统计
local stats = { success = 0, conflict = 0, soldout = 0, limited = 0, other = 0 }

response = function(status, headers, body)
  if status == 200 then
    stats.success = stats.success + 1
  elseif string.find(body, "座位被") then
    stats.conflict = stats.conflict + 1
  elseif string.find(body, "售罄") or string.find(body, "库存") then
    stats.soldout = stats.soldout + 1
  elseif status == 429 then
    stats.limited = stats.limited + 1
  else
    stats.other = stats.other + 1
  end
end

done = function(summary, latency, requests)
  io.write("\n===== 抢座压测结果 =====\n")
  io.write(string.format("总请求: %d\n", summary.requests))
  io.write(string.format("成功(排队中): %d (%.1f%%)\n", stats.success, 100.0*stats.success/summary.requests))
  io.write(string.format("座位冲突: %d (%.1f%%)\n", stats.conflict, 100.0*stats.conflict/summary.requests))
  io.write(string.format("库存不足: %d (%.1f%%)\n", stats.soldout, 100.0*stats.soldout/summary.requests))
  io.write(string.format("限流429: %d (%.1f%%)\n", stats.limited, 100.0*stats.limited/summary.requests))
  io.write(string.format("其他错误: %d\n", stats.other))
  io.write(string.format("QPS: %.0f\n", summary.requests / summary.duration * 1000000))
end
