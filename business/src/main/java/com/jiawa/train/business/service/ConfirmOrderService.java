package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.*;
import com.jiawa.train.business.enums.ConfirmOrderStatusEnum;
import com.jiawa.train.business.enums.SeatColEnum;
import com.jiawa.train.business.enums.SeatTypeEnum;
import com.jiawa.train.business.mapper.ConfirmOrderMapper;
import com.jiawa.train.business.req.ConfirmOrderDoReq;
import com.jiawa.train.business.req.ConfirmOrderQueryReq;
import com.jiawa.train.business.req.ConfirmOrderTicketReq;
import com.jiawa.train.business.resp.ConfirmOrderQueryResp;
import com.jiawa.train.common.exception.BusinessException;
import com.jiawa.train.common.exception.BusinessExceptionEnum;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ConfirmOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmOrderService.class);

    @Resource
    private DailyTrainTicketService dailyTrainTicketService;

    @Resource
    private DailyTrainCarriageService dailyTrainCarriageService;

    @Resource
    private DailyTrainSeatService dailyTrainSeatService;

    @Resource
    AfterConfirmOrderService afterConfirmOrderService;

    @Resource
    private ConfirmOrderMapper confirmOrderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void save(ConfirmOrderDoReq req) {
        DateTime now = DateTime.now();
        ConfirmOrder confirmOrder = BeanUtil.copyProperties(req, ConfirmOrder.class);
        if (ObjectUtil.isNull(confirmOrder.getId())) {
            confirmOrder.setId(SnowUtil.getSnowflakeNextId());
            confirmOrder.setCreateTime(now);
            confirmOrder.setUpdateTime(now);
            confirmOrderMapper.insert(confirmOrder);
        } else {
            confirmOrder.setUpdateTime(now);
            confirmOrderMapper.updateByPrimaryKey(confirmOrder);
        }
    }

    public PageResp<ConfirmOrderQueryResp> queryList(ConfirmOrderQueryReq req) {
        ConfirmOrderExample confirmOrderExample = new ConfirmOrderExample();
        confirmOrderExample.setOrderByClause("id desc");
        ConfirmOrderExample.Criteria criteria = confirmOrderExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<ConfirmOrder> confirmOrderList = confirmOrderMapper.selectByExample(confirmOrderExample);

        PageInfo<ConfirmOrder> pageInfo = new PageInfo<>(confirmOrderList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<ConfirmOrderQueryResp> list = BeanUtil.copyToList(confirmOrderList, ConfirmOrderQueryResp.class);

        PageResp<ConfirmOrderQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        confirmOrderMapper.deleteByPrimaryKey(id);
    }

    public void doConfirm(@Valid ConfirmOrderDoReq req) throws Exception {


        String lock = req.getDate() + "-" + req.getTrainCode() + "-lock";
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(lock, "1", 5, TimeUnit.SECONDS);
        if (BooleanUtil.isFalse(isLock)) {
            throw new BusinessException(BusinessExceptionEnum.GET_LOCK_ERROR);
        }

        try {
            List<ConfirmOrderTicketReq> tickets = req.getTickets();
            // 插入订单表
            DateTime now = DateTime.now();
            ConfirmOrder confirmOrder = new ConfirmOrder();
            confirmOrder.setId(SnowUtil.getSnowflakeNextId());
            confirmOrder.setMemberId(req.getMemberId());
            confirmOrder.setDate(req.getDate());
            confirmOrder.setTrainCode(req.getTrainCode());
            confirmOrder.setStart(req.getStart());
            confirmOrder.setEnd(req.getEnd());
            confirmOrder.setDailyTrainTicketId(req.getDailyTrainTicketId());
            confirmOrder.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
            confirmOrder.setCreateTime(now);
            confirmOrder.setUpdateTime(now);
            confirmOrder.setTickets(JSON.toJSONString(tickets));
            confirmOrderMapper.insert(confirmOrder);

            // 查询库存
            DailyTrainTicket dailyTrainTicket = dailyTrainTicketService.selectByUniqueKey(req.getDate(), req.getTrainCode(), req.getStart(), req.getEnd());
            if (ObjectUtil.isNull(dailyTrainTicket)) {
                return;
            }
            LOG.info(dailyTrainTicket.toString());

            // 预扣余票数量
            preRedcuceTickets(req, dailyTrainTicket);

            // 选座
            List<DailyTrainSeat>selectSeatList = new ArrayList<>();
            ConfirmOrderTicketReq sample = tickets.get(0);
            if(StrUtil.isNotBlank(sample.getSeat())){
                // 用户有选座
                // 获取座位的相对偏移值
                List<Integer> offset = getOffset(req, sample);
                List<DailyTrainSeat> tempSeatList = getSeat(req.getDate(), req.getTrainCode(), sample.getSeatTypeCode(),
                        sample.getSeat().split("")[0], offset,
                        dailyTrainTicket.getStartIndex(), dailyTrainTicket.getEndIndex(),
                        selectSeatList);
                if(ObjectUtil.isNotNull(tempSeatList)){
                    selectSeatList.addAll(tempSeatList);
                }
            } else {
                // 没有选座
                for (ConfirmOrderTicketReq ticket : tickets) {
                    List<DailyTrainSeat> tempSeatList = getSeat(req.getDate(), req.getTrainCode(), ticket.getSeatTypeCode(),
                            null, null,
                            dailyTrainTicket.getStartIndex(), dailyTrainTicket.getEndIndex(),
                            selectSeatList);
                    if(ObjectUtil.isNotNull(tempSeatList)){
                        selectSeatList.addAll(tempSeatList);
                    }
                }
            }
            LOG.info("选出座位数目{}", selectSeatList.size());

            // 修改数据库
            afterConfirmOrderService.afterDoConfirm(selectSeatList, dailyTrainTicket, tickets, confirmOrder);
        } finally {
            stringRedisTemplate.delete(lock);
        }

    }

    private static List<Integer> getOffset(ConfirmOrderDoReq req, ConfirmOrderTicketReq sample) {
        List<SeatColEnum> colEnumList = SeatColEnum.getColsByType(sample.getSeatTypeCode());
        // 获取座位编号列表 A1 B1 C1 D1 A2 ...D2
        List<String> colList = new ArrayList<>();
        for(int i = 1 ; i <= 2 ; i++) {
            for (SeatColEnum seatColEnum : colEnumList) {
                colList.add(seatColEnum.getCode() + i);
            }
        }
        LOG.info("座位编号数组{}", colList);
        // 计算传入座位的绝对偏移
        List<Integer> offset = new ArrayList<>();
        int firstOffset = colList.indexOf(sample.getSeat());
        for (ConfirmOrderTicketReq ticket : req.getTickets()) {
            offset.add(colList.indexOf(ticket.getSeat())-firstOffset);
        }
        LOG.info("相对偏移数组{}", offset);
        return offset;
    }

    private List<DailyTrainSeat> getSeat(Date date, String trainCode, String seatTypeCode, String column, List<Integer> offset,
                                         Integer startIndex, Integer endIndex,
                                         List<DailyTrainSeat>hasSelectSeatList) {
        // 选座列表
        List<DailyTrainSeat>selectSeatList = new ArrayList<>();

        // 根据座位类型，查出所有车厢
        List<DailyTrainCarriage> dailyTrainCarriageList = dailyTrainCarriageService.selectBySeatType(date, trainCode, seatTypeCode);
        LOG.info("车厢数量{}", dailyTrainCarriageList.size());

        // 根据车厢遍历出座位列表
        for (DailyTrainCarriage dailyTrainCarriage : dailyTrainCarriageList) {
            LOG.info("在{}号车厢选座", dailyTrainCarriage.getIndex());
            List<DailyTrainSeat> seatList = dailyTrainSeatService.selectByCarriage(dailyTrainCarriage.getIndex());
            // LOG.info("座位数为{}", seatList.size());

            // 遍历车厢中的座位
            int firstManIndex = -1;
            for (DailyTrainSeat seat : seatList) {
                firstManIndex++;
                // 如果选座了，就要判断当列值是不是对应的
                if(StrUtil.isNotBlank(column) && !column.equals(seat.getCol())) {
                    continue;
                }
                // 判断是否重复选取过
                boolean isSelectBefore = false;
                for (DailyTrainSeat hasSeat : hasSelectSeatList) {
                    if (hasSeat.getId().equals(seat.getId())) {
                        isSelectBefore = true;
                        break;
                    }
                }
                if(isSelectBefore){
                    continue;
                }
                // 判断是否可以选
                boolean available = isAvailable(startIndex, endIndex, seat);
                // 如果可选
                if(available) {
                    selectSeatList.add(seat);
                    // 如果传入了offset矩阵，还需要判断其他的座位能不能选
                    if (CollUtil.isNotEmpty(offset)) {
                        // 如果有offset矩阵，判断其他票是不是可选的
                        for (int i = 1; i < offset.size(); i++) {
                            int offIdx = offset.get(i);
                            if (offIdx + firstManIndex >= seatList.size() || !available) {
                                // 越界了或者其他票选的座位不可用
                                available = false;
                                break;
                            }
                            available = isAvailable(startIndex, endIndex, seatList.get(offIdx + firstManIndex));
                            if (available) {
                                selectSeatList.add(seatList.get(offIdx + firstManIndex));
                            }
                        }
                    }
                }
                // 其他座位校验后也满足条件
                if (available) {
                    LOG.info("seat{}可选", seat.getCarriageSeatIndex());
                    return selectSeatList;
                } else {
                    selectSeatList.clear();
                    LOG.info("seat{}不可选", seat.getCarriageSeatIndex());
                }
            }
        }
        return null;
    }

    private static boolean isAvailable(Integer startIndex, Integer endIndex, DailyTrainSeat seat) {
        // 判断当前座位是否可选
        String sell = seat.getSell();
        // 获取该区间的售卖情况
        String subSell = sell.substring(startIndex, endIndex);
        boolean available = Integer.parseInt(subSell) == 0;
        // 如果可选，修改sell
        StringBuffer sb = new StringBuffer(sell);
        sb.replace(startIndex, endIndex, "1".repeat(endIndex - startIndex));
        seat.setSell(sb.toString());
        // 说明该区间可以选
        return available;
    }


    private static void preRedcuceTickets(ConfirmOrderDoReq req, DailyTrainTicket dailyTrainTicket) {
        for (ConfirmOrderTicketReq ticket : req.getTickets()) {
            String seatTypeCode = ticket.getSeatTypeCode();
            switch (EnumUtil.getBy(SeatTypeEnum::getCode, seatTypeCode)) {
                case YDZ -> {
                    int leftTicketsNum = dailyTrainTicket.getYdz() - 1;
                    if(leftTicketsNum < 0) {
                        throw new BusinessException(BusinessExceptionEnum.Confirm_Order_Not_Enough_ERROR);
                    }
                    dailyTrainTicket.setYdz(leftTicketsNum);
                }
                case EDZ -> {
                    int leftTicketsNum = dailyTrainTicket.getEdz() - 1;
                    if(leftTicketsNum < 0) {
                        throw new BusinessException(BusinessExceptionEnum.Confirm_Order_Not_Enough_ERROR);
                    }
                    dailyTrainTicket.setEdz(leftTicketsNum);
                }
                case RW -> {
                    int leftTicketsNum = dailyTrainTicket.getRw() - 1;
                    if(leftTicketsNum < 0) {
                        throw new BusinessException(BusinessExceptionEnum.Confirm_Order_Not_Enough_ERROR);
                    }
                    dailyTrainTicket.setRw(leftTicketsNum);
                }
                case YW -> {
                    int leftTicketsNum = dailyTrainTicket.getYw() - 1;
                    if(leftTicketsNum < 0) {
                        throw new BusinessException(BusinessExceptionEnum.Confirm_Order_Not_Enough_ERROR);
                    }
                    dailyTrainTicket.setYw(leftTicketsNum);
                }
            }
        }
    }
}
