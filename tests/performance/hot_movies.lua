-- wrk 压测脚本：电影列表读接口（验证多级缓存命中率）
-- 用法: wrk -t4 -c100 -d30s -s hot_movies.lua http://localhost:8080
-- 对比：开缓存 vs 关缓存 的 QPS 和 RT

wrk.method = "GET"

request = function()
  return wrk.format("GET", "/movies/hot")
end

done = function(summary, latency, requests)
  io.write("\n===== 电影列表读压测 =====\n")
  io.write(string.format("QPS: %.0f\n", summary.requests / summary.duration * 1000000))
  io.write(string.format("P50: %.2fms\n", latency:percentile(50)))
  io.write(string.format("P99: %.2fms\n", latency:percentile(99)))
end
