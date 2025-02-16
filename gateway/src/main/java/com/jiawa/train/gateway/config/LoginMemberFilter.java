package com.jiawa.train.gateway.config;

import cn.hutool.core.util.StrUtil;
import com.jiawa.train.gateway.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoginMemberFilter implements GlobalFilter, Ordered {

    private final Logger LOG = LoggerFactory.getLogger(LoginMemberFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 如果是这些路径 就放行
        if (path.contains("/admin")
            || path.contains("/hello")
            || path.contains("/member/member/login")
            || path.contains("/member/member/send-code")) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        // token 不为空
        if (StrUtil.isNotBlank(token)) {
            boolean validate = JwtUtil.validate(token);
            // token校验成功
            if (validate) {
                return chain.filter(exchange);
            }

        }
        // token为空或者token校验失败
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
