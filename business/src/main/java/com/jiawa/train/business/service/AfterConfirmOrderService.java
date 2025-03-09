package com.jiawa.train.business.service;

import com.jiawa.train.business.domain.ConfirmOrder;
import com.jiawa.train.business.domain.DailyTrainSeat;
import com.jiawa.train.business.domain.DailyTrainTicket;
import com.jiawa.train.business.enums.ConfirmOrderStatusEnum;
import com.jiawa.train.business.feign.MemberFeign;
import com.jiawa.train.business.mapper.ConfirmOrderMapper;
import com.jiawa.train.business.mapper.DailyTrainSeatMapper;
import com.jiawa.train.business.mapper.cust.DailyTrainTicketMapperCust;
import com.jiawa.train.business.req.ConfirmOrderTicketReq;
import com.jiawa.train.common.req.MemberTicketReq;
import com.jiawa.train.common.resp.CommonResp;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AfterConfirmOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(AfterConfirmOrderService.class);

    @Resource
    private DailyTrainSeatMapper dailyTrainSeatMapper;

    @Resource
    private DailyTrainTicketMapperCust dailyTrainTicketMapperCust;

    @Resource
    private MemberFeign memberFeign;

    @Resource
    private ConfirmOrderMapper confirmOrderMapper;

    // @Transactional
    @GlobalTransactional
    public void afterDoConfirm(List<DailyTrainSeat> selectSeatList, DailyTrainTicket dailyTrainTicket, List<ConfirmOrderTicketReq> tickets, ConfirmOrder confirmOrder) throws Exception {

        int startIndex = dailyTrainTicket.getStartIndex();
        int endIndex = dailyTrainTicket.getEndIndex();
        // 更新座位的sell信息
        for (int i = 0 ; i < selectSeatList.size() ; i++) {
            DailyTrainSeat selectSeat = selectSeatList.get(i);
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

            // 调用会员服务接口，为会员增加一张车票
            MemberTicketReq memberTicketReq = new MemberTicketReq();
            memberTicketReq.setMemberId(confirmOrder.getMemberId());
            memberTicketReq.setPassengerId(tickets.get(i).getPassengerId());
            memberTicketReq.setPassengerName(tickets.get(i).getPassengerName());
            memberTicketReq.setTrainDate(dailyTrainTicket.getDate());
            memberTicketReq.setTrainCode(dailyTrainTicket.getTrainCode());
            memberTicketReq.setCarriageIndex(selectSeat.getCarriageIndex());
            memberTicketReq.setSeatRow(selectSeat.getRow());
            memberTicketReq.setSeatCol(selectSeat.getCol());
            memberTicketReq.setStartStation(dailyTrainTicket.getStart());
            memberTicketReq.setStartTime(dailyTrainTicket.getStartTime());
            memberTicketReq.setEndStation(dailyTrainTicket.getEnd());
            memberTicketReq.setEndTime(dailyTrainTicket.getEndTime());
            memberTicketReq.setSeatType(selectSeat.getSeatType());
            CommonResp<Object> commonResp = memberFeign.save(memberTicketReq);
            LOG.info("调用member接口，返回：{}", commonResp);

            // 更新订单状态为成功
            ConfirmOrder confirmOrderForUpdate = new ConfirmOrder();
            confirmOrderForUpdate.setId(confirmOrder.getId());
            confirmOrderForUpdate.setUpdateTime(new Date());
            confirmOrderForUpdate.setStatus(ConfirmOrderStatusEnum.SUCCESS.getCode());
            confirmOrderMapper.updateByPrimaryKeySelective(confirmOrderForUpdate);

            // if(1==1) {
            //     throw new Exception("测试异常");
            // }
        }
    }
}
