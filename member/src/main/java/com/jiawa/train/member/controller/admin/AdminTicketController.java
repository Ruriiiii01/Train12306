package com.jiawa.train.member.controller.admin;

import com.jiawa.train.common.req.MemberTicketReq;
import com.jiawa.train.common.resp.CommonResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.member.req.TicketQueryReq;
import com.jiawa.train.member.resp.TicketQueryResp;
import com.jiawa.train.member.service.TicketService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/ticket")
public class AdminTicketController {

    @Resource
    private TicketService ticketService;

    @PostMapping("/save")
    public CommonResp<Object> save(@Valid @RequestBody MemberTicketReq req) {
        ticketService.save(req);
        return new CommonResp<>();
    }

    @GetMapping("/query-list")
    public CommonResp<PageResp<TicketQueryResp>> queryList(@Valid TicketQueryReq req) {
        PageResp<TicketQueryResp> list = ticketService.queryList(req);
        return new CommonResp<>(list);
    }

    @DeleteMapping("/delete/{id}")
    public CommonResp<Object> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return new CommonResp<>();
    }

}
