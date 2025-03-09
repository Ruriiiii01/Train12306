package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.*;
import com.jiawa.train.business.enums.SeatTypeEnum;
import com.jiawa.train.business.enums.TrainTypeEnum;
import com.jiawa.train.business.mapper.DailyTrainTicketMapper;
import com.jiawa.train.business.req.DailyTrainTicketQueryReq;
import com.jiawa.train.business.req.DailyTrainTicketSaveReq;
import com.jiawa.train.business.resp.DailyTrainTicketQueryResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class DailyTrainTicketService {

    private static final Logger LOG = LoggerFactory.getLogger(DailyTrainTicketService.class);

    @Resource
    private TrainStationService trainStationService;

    @Resource
    private DailyTrainSeatService dailyTrainSeatService;

    @Resource
    private DailyTrainTicketMapper dailyTrainTicketMapper;

    public void save(DailyTrainTicketSaveReq req) {
        DateTime now = DateTime.now();
        DailyTrainTicket dailyTrainTicket = BeanUtil.copyProperties(req, DailyTrainTicket.class);
        if (ObjectUtil.isNull(dailyTrainTicket.getId())) {
            dailyTrainTicket.setId(SnowUtil.getSnowflakeNextId());
            dailyTrainTicket.setCreateTime(now);
            dailyTrainTicket.setUpdateTime(now);
            dailyTrainTicketMapper.insert(dailyTrainTicket);
        } else {
            dailyTrainTicket.setUpdateTime(now);
            dailyTrainTicketMapper.updateByPrimaryKey(dailyTrainTicket);
        }
    }

    @Cacheable(value = "DailyTrainTicketService.queryList")
    public PageResp<DailyTrainTicketQueryResp> queryList(DailyTrainTicketQueryReq req) {
        DailyTrainTicketExample dailyTrainTicketExample = new DailyTrainTicketExample();
        dailyTrainTicketExample.setOrderByClause("id asc");
        DailyTrainTicketExample.Criteria criteria = dailyTrainTicketExample.createCriteria();

        if (ObjUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        if (ObjUtil.isNotEmpty(req.getStart())) {
            criteria.andStartEqualTo(req.getStart());
        }
        if (ObjUtil.isNotEmpty(req.getEnd())) {
            criteria.andEndEqualTo(req.getEnd());
        }

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<DailyTrainTicket> dailyTrainTicketList = dailyTrainTicketMapper.selectByExample(dailyTrainTicketExample);

        PageInfo<DailyTrainTicket> pageInfo = new PageInfo<>(dailyTrainTicketList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<DailyTrainTicketQueryResp> list = BeanUtil.copyToList(dailyTrainTicketList, DailyTrainTicketQueryResp.class);

        PageResp<DailyTrainTicketQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        dailyTrainTicketMapper.deleteByPrimaryKey(id);
    }

    public List<DailyTrainTicket> queryAll() {
        DailyTrainTicketExample dailyTrainTicketExample = new DailyTrainTicketExample();
        dailyTrainTicketExample.setOrderByClause("id asc");
        return dailyTrainTicketMapper.selectByExample(dailyTrainTicketExample);
    }

    @Transactional
    public void genDaily(Date date, String trainCode, DailyTrain dailyTrain) {

        // 删除
        DailyTrainTicketExample dailyTicketExample = new DailyTrainTicketExample();
        dailyTicketExample.createCriteria().andTrainCodeEqualTo(trainCode).andDateEqualTo(date);
        dailyTrainTicketMapper.deleteByExample(dailyTicketExample);

        // 生成
        List<TrainStation> trainList = trainStationService.selectByTrainCode(trainCode);
        if(CollUtil.isEmpty(trainList)) {
            return;
        }

        // 计算余票信息
        BigDecimal priceRate = EnumUtil.getFieldBy(TrainTypeEnum::getPriceRate, TrainTypeEnum::getCode, dailyTrain.getType());
        DateTime now = DateTime.now();
        for(int i = 0 ; i < trainList.size() ; i++) {
            TrainStation trainStationStart = trainList.get(i);
            BigDecimal distance = new BigDecimal(0);
            for (int j = i + 1 ; j < trainList.size() ; j++) {
                TrainStation trainStationEnd = trainList.get(j);
                // 计算距离
                distance = distance.add(trainStationEnd.getKm());
                DailyTrainTicket dailyTrainTicket = new DailyTrainTicket();
                dailyTrainTicket.setId(SnowUtil.getSnowflakeNextId());
                dailyTrainTicket.setDate(date);
                dailyTrainTicket.setTrainCode(trainCode);
                dailyTrainTicket.setStart(trainStationStart.getName());
                dailyTrainTicket.setStartPinyin(trainStationStart.getNamePinyin());
                dailyTrainTicket.setStartTime(trainStationStart.getOutTime());
                dailyTrainTicket.setStartIndex(trainStationStart.getIndex());
                dailyTrainTicket.setEnd(trainStationEnd.getName());
                dailyTrainTicket.setEndPinyin(trainStationEnd.getNamePinyin());
                dailyTrainTicket.setEndTime(trainStationEnd.getInTime());
                dailyTrainTicket.setEndIndex(trainStationEnd.getIndex());
                dailyTrainTicket.setYdz(dailyTrainSeatService.countSeat(trainCode, date, SeatTypeEnum.YDZ));
                dailyTrainTicket.setYdzPrice(distance.multiply(SeatTypeEnum.YDZ.getPrice()).multiply(priceRate));
                dailyTrainTicket.setEdz(dailyTrainSeatService.countSeat(trainCode, date, SeatTypeEnum.EDZ));
                dailyTrainTicket.setEdzPrice(distance.multiply(SeatTypeEnum.EDZ.getPrice()).multiply(priceRate));
                dailyTrainTicket.setRw(dailyTrainSeatService.countSeat(trainCode, date, SeatTypeEnum.RW));
                dailyTrainTicket.setRwPrice(distance.multiply(SeatTypeEnum.RW.getPrice()).multiply(priceRate));
                dailyTrainTicket.setYw(dailyTrainSeatService.countSeat(trainCode, date, SeatTypeEnum.YW));
                dailyTrainTicket.setYwPrice(distance.multiply(SeatTypeEnum.YW.getPrice()).multiply(priceRate));
                dailyTrainTicket.setCreateTime(now);
                dailyTrainTicket.setUpdateTime(now);

                dailyTrainTicketMapper.insert(dailyTrainTicket);
            }

        }
    }

    public DailyTrainTicket selectByUniqueKey(Date date, String trainCode, String start, String end) {
        DailyTrainTicketExample example = new DailyTrainTicketExample();
        example.setOrderByClause("id asc");
        example.createCriteria()
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode)
                .andStartEqualTo(start)
                .andEndEqualTo(end);
        List<DailyTrainTicket> list = dailyTrainTicketMapper.selectByExample(example);
        if(CollUtil.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }
}
