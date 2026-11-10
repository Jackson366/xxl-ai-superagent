package com.xxl.ai.api.framework.web.xxlsso;

import com.xxl.ai.api.framework.util.CacheStore;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.sso.core.store.LoginStore;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * LoginStore：登录态存于 CacheStore（默认 Redis；local profile 为进程内缓存，重启后需重新登录）
 *
 * @author xuxueli 2025-08-03
 */
@Component
public class RedisLoginStore implements LoginStore {


    @Value("${xxl-sso.store.redis.keyprefix}")
    private String storeKeyPrefix;

    @Resource
    private CacheStore cacheStore;

    /**
     * parse store key from userId
     *
     * <pre>
     *     key: "xxl_sso_user:" + {user001}
     *     value: loginInfo
     * </pre>>
     *
     * @param userId
     * @return
     */
    private String parseStoreKey(String userId){
        return storeKeyPrefix + userId;
    }

    @Override
    public Response<String> set(LoginInfo loginInfo) {

        // valid loginInfo
        if (loginInfo == null
                || StringTool.isBlank(loginInfo.getUserId())
                || StringTool.isBlank(loginInfo.getSignature())) {
            return Response.ofFail("loginInfo invalid.");
        }

        // valid expire-time
        if (loginInfo.getExpireTime() < System.currentTimeMillis()) {
            return Response.ofFail("expireTime invalid.");
        }

        // generate redis timeout (seconds)
        long seconds = (loginInfo.getExpireTime() - System.currentTimeMillis()) / 1000;

        // generate storeKey
        String storeKey = parseStoreKey(loginInfo.getUserId());

        // write
        cacheStore.setObject(storeKey, loginInfo, seconds, TimeUnit.SECONDS);
        return Response.ofSuccess();
    }

    @Override
    public Response<String> update(LoginInfo loginInfo) {

        // valid loginInfo
        if (loginInfo == null || StringTool.isBlank(loginInfo.getUserId())) {
            return Response.ofFail("loginInfo invalid.");
        }

        // generate storeKey
        String storeKey = parseStoreKey(loginInfo.getUserId());

        // valid expire-time
        if (loginInfo.getExpireTime() < System.currentTimeMillis()) {
            return Response.ofFail("expireTime invalid.");
        }

        // read
        LoginInfo loginInfoStore = cacheStore.getObject(storeKey);
        if (loginInfoStore == null) {
            return Response.ofFail("loginInfo not exists.");
        }

        // update LoginInfo
        loginInfoStore.setUserName(loginInfo.getUserName());
        loginInfoStore.setRealName(loginInfo.getRealName());
        loginInfoStore.setExtraInfo(loginInfo.getExtraInfo());
        loginInfoStore.setRoleList(loginInfo.getRoleList());
        loginInfoStore.setPermissionList(loginInfo.getPermissionList());
        loginInfoStore.setExpireTime(loginInfo.getExpireTime());

        // generate redis timeout (seconds)
        long seconds = (loginInfo.getExpireTime() - System.currentTimeMillis()) / 1000;

        // write
        cacheStore.setObject(storeKey, loginInfoStore, seconds, TimeUnit.SECONDS);
        return Response.ofSuccess();
    }

    @Override
    public Response<String> remove(String userId) {

        // valid userId
        if (StringTool.isBlank(userId)) {
            return Response.ofFail("userId invalid.");
        }

        // generate storeKey
        String storeKey = parseStoreKey(userId);

        // remove
        cacheStore.delete(storeKey);
        return Response.ofSuccess();
    }

    @Override
    public Response<LoginInfo> get(String userId) {

        // valid userId
        if (StringTool.isBlank(userId)) {
            return Response.ofFail("userId invalid.");
        }

        // generate storeKey
        String storeKey = parseStoreKey(userId);

        // read
        LoginInfo loginInfo = cacheStore.getObject(storeKey);
        if (loginInfo == null) {
            return Response.ofFail("loginInfo not exists.");
        }

        // valid expire time
        if (loginInfo.getExpireTime() < System.currentTimeMillis()) {
            cacheStore.delete(storeKey);
            return Response.ofFail("loginInfo is timeout");
        }

        return Response.ofSuccess(loginInfo);
    }

    @Override
    public Response<String> createTicket(String ticket, String token, long ticketTimeout) {
        return Response.ofFail("not support.");
    }

    @Override
    public Response<String> validTicket(String ticket) {
        return Response.ofFail("not support.");
    }

}
