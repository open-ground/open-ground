package io.github.openground.cloud.loadbalance;

import cn.hutool.core.net.NetUtil;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.balancer.NacosBalancer;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>Description: nacos 本地服务优先负载均衡器</P>
 *
 * @Author:jack.zhang
 * @Version 3.0.0
 * @Date 2024/2/26 19:27
 * @return
 */
@Slf4j
public class NacosLocalFirstLoadBalancer implements ReactorServiceInstanceLoadBalancer {


    private final String serviceId;

    private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    private Set<String> localIps;

    public NacosLocalFirstLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider, String serviceId, NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
        // 使用hutool 工具获取本机IP地址
        this.localIps = NetUtil.localIpv4s();
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get().next().map(this::getInstanceResponse);
    }

    /**
     * 优先获取与本地IP一致的服务，否则获取同一集群服务
     *
     * @param serviceInstances
     * @return
     */
    private Response<ServiceInstance> getInstanceResponse(
            List<ServiceInstance> serviceInstances) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        // 过滤与本机IP地址一样的服务实例
        if (!CollectionUtils.isEmpty(this.localIps)) {
            for (ServiceInstance instance : serviceInstances) {
                String host = instance.getHost();
                if (this.localIps.contains(host)) {
                    return new DefaultResponse(instance);
                }
            }
        }
        return this.getClusterInstanceResponse(serviceInstances);
    }

    /**
     * 同一集群下优先获取
     *
     * @param serviceInstances
     * @return
     */
    private Response<ServiceInstance> getClusterInstanceResponse(
            List<ServiceInstance> serviceInstances) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }

        try {
            String clusterName = this.nacosDiscoveryProperties.getClusterName();

            List<ServiceInstance> instancesToChoose = serviceInstances;
            if (StringUtils.isNotBlank(clusterName)) {
                List<ServiceInstance> sameClusterInstances = serviceInstances.stream()
                        .filter(serviceInstance -> {
                            String cluster = serviceInstance.getMetadata().get("nacos.cluster");
                            return StringUtils.equals(cluster, clusterName);
                        }).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(sameClusterInstances)) {
                    instancesToChoose = sameClusterInstances;
                }
            } else {
                log.warn(
                        "A cross-cluster call occurs，name = {}, clusterName = {}, instance = {}",
                        serviceId, clusterName, serviceInstances);
            }

            ServiceInstance instance = NacosBalancer.getHostByRandomWeight3(instancesToChoose);
            return new DefaultResponse(instance);
        } catch (Exception e) {
            log.warn("NacosLoadBalancer error", e);
            return null;
        }
    }
}
