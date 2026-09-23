-- wrk 压测前置脚本：登录拿 accessToken
-- 用法: wrk -t1 -c1 -d1s -s login.lua http://localhost:8080
-- 输出 token 到文件，供其他压测脚本引用

wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"

request = function()
  wrk.body = '{"phone":"13800000001","password":"123456"}'
  return wrk.format("POST", "/auth/login")
end

response = function(status, headers, body)
  -- 从响应里提取 accessToken
  local token = string.match(body, '"accessToken":"([^"]+)"')
  if token then
    local f = io.open("/tmp/wrk_token.txt", "w")
    f:write(token)
    f:close()
    print("Token saved: " .. string.sub(token, 1, 30) .. "...")
  end
end
