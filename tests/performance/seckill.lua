-- wrk 压测脚本：抢座接口
-- 用法: wrk -t4 -c200 -d30s -s seckill.lua http://localhost:8080
-- 需要先登录拿 token，这里简化为直接在脚本里拼 Authorization

local token = os.getenv("TEST_TOKEN") or "your-access-token"
local scheduleId = os.getenv("SCHEDULE_ID") or "1"

wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer " .. token

request = function()
  local row = math.random(1, 8)
  local col = math.random(1, 10)
  local requestId = "perf_" .. os.time() .. "_" .. math.random(100000)
  local body = string.format(
    '{"scheduleId":%s,"seats":[{"row":%d,"col":%d}],"requestId":"%s"}',
    scheduleId, row, col, requestId
  )
  wrk.body = body
  return wrk.format("POST", "/seckill/seize")
end
