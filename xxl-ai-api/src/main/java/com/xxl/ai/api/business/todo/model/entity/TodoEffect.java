package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理副作用队列 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoEffect {

    private long id;             /* 副作用ID（自增顺序即队列位置） */
    private String kind;         /* 类型：assessment、launch、delivery、reply */
    private String effectKey;    /* 对象键，同类型同对象合并为一条 */
    private String payload;      /* 参数(JSON) */
    private int generation;      /* 参数版本，执行中的旧版本不删除新参数 */
    private int attempts;        /* 连续失败次数 */
    private Date dueTime;        /* 下次可执行时间 */
    private String error;        /* 最近一次错误 */
    private String owner;        /* 抢占的实例标识 */
    private Date ownerUntil;     /* 抢占有效期，过期可被其他实例接手 */
    private Date addTime;        /* 新增时间 */
    private Date updateTime;     /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getEffectKey() {
        return effectKey;
    }

    public void setEffectKey(String effectKey) {
        this.effectKey = effectKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Date getDueTime() {
        return dueTime;
    }

    public void setDueTime(Date dueTime) {
        this.dueTime = dueTime;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Date getOwnerUntil() {
        return ownerUntil;
    }

    public void setOwnerUntil(Date ownerUntil) {
        this.ownerUntil = ownerUntil;
    }

    public Date getAddTime() {
        return addTime;
    }

    public void setAddTime(Date addTime) {
        this.addTime = addTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

}
