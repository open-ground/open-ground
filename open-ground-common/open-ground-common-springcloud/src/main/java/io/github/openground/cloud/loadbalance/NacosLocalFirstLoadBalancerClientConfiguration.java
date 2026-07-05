package io.github.openground.cloud.loadbalance;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;


/**
 * <p>Title: GroundNacosLocalFirstLoadBalancerClientConfiguration</p>
 * <p>Description: nacos 负载均衡同IP 同区域有限</P>
 *
 * @Author:jack.zhang
 * @Date 2024/2/26 19:30
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
//// 这里引入 nacos 默认客户端配置，否则的话需要添加 配置 spring.cloud.loadbalancer.nacos.enabled = true
@Slf4j
public class NacosLocalFirstLoadBalancerClientConfiguration {

    /**
     * 本地优先策略
     *
     * @param environment               环境变量
     * @param loadBalancerClientFactory 工厂
     * @param nacosDiscoveryProperties  属性
     * @return ReactorLoadBalancer
     */
    @Bean
    @ConditionalOnNacosDiscoveryEnabled
    @ConditionalOnProperty(value = "spring.cloud.loadbalancer.local-first", havingValue = "true")
    public ReactorLoadBalancer<ServiceInstance> nacosLocalFirstLoadBalancer(Environment environment,
                                                                            LoadBalancerClientFactory loadBalancerClientFactory,
                                                                            NacosDiscoveryProperties nacosDiscoveryProperties) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        log.info("Use nacos local first load balancer for {} service", name);
        return new NacosLocalFirstLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name, nacosDiscoveryProperties);
    }
}


