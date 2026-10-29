package org.hcz;

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
@MapperScan(basePackages = "org.hcz.**.mapper")
@EnableScheduling
public class HczApplication {

    public static void main(String[] args) {
        // 权限校验开关由 AuthStore 默认启用，不再在启动前设置全局静态状态。
        SpringApplication.run(HczApplication.class, args);

    }


}
