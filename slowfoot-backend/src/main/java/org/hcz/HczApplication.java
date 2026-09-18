package org.hcz;

import org.hcz.authority.AuthStore;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableAsync
@EnableAspectJAutoProxy
@SpringBootApplication
@EnableTransactionManagement
@MapperScan(basePackages = "org.hcz.mapper")
@EnableScheduling
public class HczApplication {

    public static void main(String[] args) {
        AuthStore.enable();
        SpringApplication.run(HczApplication.class, args);

    }


}
