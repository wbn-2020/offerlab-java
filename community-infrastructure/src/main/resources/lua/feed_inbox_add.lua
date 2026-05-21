-- feed_inbox_add.lua
-- 收件箱原子写入 + 容量裁剪 + TTL 续期
-- KEYS[1] feed:inbox:{uid}
-- ARGV[1] postId
-- ARGV[2] timestamp ms
-- ARGV[3] cap (1000)
-- ARGV[4] ttl s

redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])

local size = redis.call('ZCARD', KEYS[1])
local cap = tonumber(ARGV[3])
if size > cap then
    redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - cap - 1)
end

redis.call('EXPIRE', KEYS[1], ARGV[4])
return 1
