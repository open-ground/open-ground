package io.github.openground.cloud.datasource;

import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.common.config.condition.ConditionalOnService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 数据源自动配置类
 */
@Slf4j
@Configuration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@EnableFeignClients(basePackages = "io.github.openground.cloud.auth")
@ComponentScan(basePackages = "io.github.openground.common.datasource")
@MapperScan(basePackages = "io.github.openground.common.datasource.mapper")
public class FeignDatasourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeignSysDatasourceProvider.class)
    public FeignSysDatasourceProvider feignSysDatasourceProvider(AuthFeignClient feignClient) {
        log.info("Feign获取数据源已启用（远程调用 Auth 获取数据源）");
        return new FeignSysDatasourceProvider(feignClient);
    }
}
