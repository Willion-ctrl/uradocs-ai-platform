package com.auradoc.auradocbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.auradoc.auradocbackend.mapper")
public class AuradocBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuradocBackendApplication.class, args);
    }

}
