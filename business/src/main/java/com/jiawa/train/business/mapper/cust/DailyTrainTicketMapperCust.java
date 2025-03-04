package com.jiawa.train.business.mapper.cust;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface DailyTrainTicketMapperCust {
    // int updateBySell(Date date, String trainCode, String seatTypeCode
    //         ,Integer minStart, Integer maxStart, Integer minEnd, Integer maxEnd);
    int updateBySell(@Param("date") Date date,
                     @Param("trainCode") String trainCode,
                     @Param("seatTypeCode") String seatTypeCode,
                     @Param("minStart") int minStart,
                     @Param("maxStart") int maxStart,
                     @Param("minEnd") int minEnd,
                     @Param("maxEnd") int maxEnd);
}