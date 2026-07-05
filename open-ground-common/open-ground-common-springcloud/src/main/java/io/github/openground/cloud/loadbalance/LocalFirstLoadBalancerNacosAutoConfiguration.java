package io.github.openground.cloud.loadbalance;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Title: LocalFirstLoadBalancerNacosAutoConfiguration</p>
 * <p>Description: Nacos 本地优先负载均衡自动配置</P>
 *
 * @Author:jack.zhang
 * @Date 2024/2/26 19:33
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnNacosDiscoveryEnabled
@LoadBalancerClients(defaultConfiguration = NacosLocalFirstLoadBalancerClientConfiguration.class)
public class LocalFirstLoadBalancerNacosAutoConfiguration {

}
