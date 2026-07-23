package com.pathocheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PathoCheck 后端程序入口。
 *
 * 这个 main 方法只负责启动 Spring Boot 服务器。
 * 真正的业务流程位于 PathoCheckService。
 */
@SpringBootApplication
public class PathoCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(PathoCheckApplication.class, args);
    }
}
