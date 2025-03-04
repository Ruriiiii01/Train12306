package com.jiawa.train.business.feign;

import com.jiawa.train.common.req.MemberTicketReq;
import com.jiawa.train.common.resp.CommonResp;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "member", url = "127.0.0.1:8001/member")
public interface MemberFeign {
    @GetMapping("/hello")
    String hello();

    @PostMapping("/feign/ticket/save")
    CommonResp<Object> save(@Valid @RequestBody MemberTicketReq req);
}
