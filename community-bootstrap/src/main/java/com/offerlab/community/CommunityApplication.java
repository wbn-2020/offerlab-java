package com.offerlab.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.offerlab.community")
@MapperScan(basePackages = "com.offerlab.community", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class CommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityApplication.class, args);
    }
}

