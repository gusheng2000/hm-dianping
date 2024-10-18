--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]
--2.key参数
--2.1库存key
local stockKey = "secKill:stock:" .. voucherId
-- 2.2订单key
local orderKey = "secKill:order:" .. voucherId
--3脚本业务
--3.1判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    --库存不足
    return 1
end
--3.2判断是否重复下单
if (redis.call("sismember", orderKey, userId) == 1) then
    --存在重复下单
    return 2
end
--3.3扣库存 incrby 1
redis.call("incrby", stockKey, -1)
--3.4下单
redis.call("sadd", orderKey, userId)
return 0