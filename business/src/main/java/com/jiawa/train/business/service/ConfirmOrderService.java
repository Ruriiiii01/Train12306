package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.ConfirmOrder;
import com.jiawa.train.business.domain.ConfirmOrderExample;
import com.jiawa.train.business.domain.DailyTrainTicket;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfirmOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmOrderService.class);

    @Resource
    private DailyTrainTicketService dailyTrainTicketService;

    @Resource
    private ConfirmOrderMapper confirmOrderMapper;

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

    public void doConfirm(@Valid ConfirmOrderDoReq req) {
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
        confirmOrder.setTickets(JSON.toJSONString(req.getTickets()));
        confirmOrderMapper.insert(confirmOrder);

        // 查询库存
        DailyTrainTicket dailyTrainTicket = dailyTrainTicketService.selectByUniqueKey(req.getDate(), req.getTrainCode(), req.getStart(), req.getEnd());
        if (ObjectUtil.isNull(dailyTrainTicket)) {
            return;
        }
        LOG.info(dailyTrainTicket.toString());

        // 预扣余票数量
        preRedcuceTickets(req, dailyTrainTicket);

        // 获取座位的相对偏移值
        ConfirmOrderTicketReq sample = req.getTickets().get(0);
        if(StrUtil.isNotBlank(sample.getSeat())){ // 传入了选的座位 比如A1 B2
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
        }
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
