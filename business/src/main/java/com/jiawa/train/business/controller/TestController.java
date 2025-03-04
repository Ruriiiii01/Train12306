package com.jiawa.train.business.controller;

import com.jiawa.train.business.feign.MemberFeign;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Resource
    private MemberFeign memberFeign;

    @GetMapping("/hello")
    public String hello() {
        return "hello world! business! " + memberFeign.hello();
    }

}
