package com.xxl.ai.api.framework.util;

import java.util.concurrent.TimeUnit;

/**
 * 缓存存储：默认 Redis 实现（RedisCacheUtil），local profile 为进程内实现（LocalCacheStore）
 *
 * @author xxl-ai 2026-11-10
 */
public interface CacheStore {

    /**
     * 写入缓存对象（带过期时间）
     */
    <T> void setObject(String key, T value, long timeout, TimeUnit unit);

    /**
     * 不存在时写入（带过期时间），用于简单互斥锁
     *
     * @return 写入成功返回 true，已存在返回 false
     */
    boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit);

    /**
     * 读取缓存对象：不存在或已过期返回 null
     */
    <T> T getObject(String key);

    /**
     * 删除缓存
     *
     * @return 存在并删除返回 true
     */
    boolean delete(String key);

}
