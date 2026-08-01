package com.aieducenter.demobff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo 消费方 BFF（issue #16）——OIDC Authorization Code Flow 消费方参考实现。
 * token 只存服务端内存会话，浏览器只持业务 cookie。
 */
@SpringBootApplication
public class DemoBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoBffApplication.class, args);
    }
}
