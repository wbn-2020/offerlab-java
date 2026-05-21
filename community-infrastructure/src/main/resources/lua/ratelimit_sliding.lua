-- ratelimit_sliding.lua
-- 滑动窗口限流
-- KEYS[1] rate limit key
-- ARGV[1] now ms
-- ARGV[2] window ms
-- ARGV[3] limit
-- ARGV[4] ttl s
-- 返回：1 通过；0 限流

local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
local count = redis.call('ZCARD', KEYS[1])
if count >= limit then
    return 0
end
redis.call('ZADD', KEYS[1], now, now)
redis.call('EXPIRE', KEYS[1], ttl)
return 1
