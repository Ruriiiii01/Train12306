package com.jiawa.train.common.util;

import cn.hutool.core.util.IdUtil;

public class SnowUtil {
    private static final long dataCenterId = 1;
    private static final long workerId = 1;

    public static long getSnowflakeNextId() {
        return IdUtil.getSnowflake(dataCenterId, workerId).nextId();
    }

    public static String getSnowflakeNextIdStr() {
        return IdUtil.getSnowflake(dataCenterId, workerId).nextIdStr();
    }
}
