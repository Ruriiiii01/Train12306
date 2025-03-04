package com.jiawa.train.business.service;

import com.jiawa.train.business.domain.DailyTrainSeat;
import com.jiawa.train.business.mapper.DailyTrainSeatMapper;
import com.jiawa.train.business.mapper.cust.DailyTrainTicketMapperCust;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AfterConfirmOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(AfterConfirmOrderService.class);

    @Resource
    private DailyTrainSeatMapper dailyTrainSeatMapper;

    @Resource
    private DailyTrainTicketMapperCust dailyTrainTicketMapperCust;

    @Transactional
    public void afterDoConfirm(List<DailyTrainSeat> selectSeatList, int startIndex, int endIndex) {
        // 更新座位的sell信息
        for (DailyTrainSeat selectSeat : selectSeatList) {
            DailyTrainSeat seatForUpdate = new DailyTrainSeat();
            seatForUpdate.setId(selectSeat.getId());
            seatForUpdate.setSell(selectSeat.getSell());
            dailyTrainSeatMapper.updateByPrimaryKeySelective(seatForUpdate);
            // 扣减每个区间的库存
            // 需要扣减的区间为：与选座有交集的区间 并且 该区间的这个票是可以买的
            // startIndex --- 选座区间的开始下标
            // endIndex --- 选座区间的结束下标
            // effectStartMin --- 受到影响区间的开始下标
            // effectEndMax --- 受到影响区间的结束下标
            // effectStartMin --- 收到影响区间的开始下标
            String sell = seatForUpdate.getSell();
            int effectStartMin = startIndex - 1;
            while (effectStartMin>=0 && sell.charAt(effectStartMin) == '0') {
                effectStartMin--;
            }
            effectStartMin++;
            int effectStartMax = endIndex - 1;

            int effectEndMin = startIndex + 1;
            int effectEndMax = endIndex + 1;
            while (effectEndMax < sell.length() && sell.charAt(effectEndMax) == '0') {
                effectEndMax++;
            }
            LOG.info("影响区间[{},{}]--[{},{}]", effectStartMin, effectStartMax, effectEndMin, effectEndMax);
            LOG.info("SeatType {}", selectSeat.getSeatType());
            dailyTrainTicketMapperCust.updateBySell(
                    selectSeat.getDate(),
                    selectSeat.getTrainCode(),
                    selectSeat.getSeatType(),
                    effectStartMin,
                    effectStartMax,
                    effectEndMin,
                    effectEndMax);
        }
    }
}
