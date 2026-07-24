package io.github.openground.cloud.auth;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.keygen.KeyInfoDomain;
import io.github.openground.common.log.domain.SysOptLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Auth 服务 Feign 客户端（统一）
 * <p>远程调用 Auth 服务的核心接口：Token 校验、序列号生成、操作日志记录。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FeignClient(name = "${ground.auth.application.name: imap-center-auth}",
             path = "${ground.auth.server.context-path: /auth/uaa}",
             url = "${ground.auth.url:}")
public interface AuthFeignClient {

    /**
     * Token 校验
     *
     * @return 校验结果
     */
    @PostMapping("/funs/tokenCheck")
    CommonResult tokenCheck();

    /**
     * 远程获取并推进序列
     *
     * @param keyinfo 序列请求信息（pkName）
     * @return 包含 KeyInfoDomain 的 CommonResult
     */
    @PostMapping("/comm/api/retrieveKeyFromDB")
    CommonResult retrieveKeyFromDB(@RequestBody KeyInfoDomain keyinfo);

    /**
     * 记录操作日志
     *
     * @param sysOptLog 操作日志对象
     * @return 调用结果
     */
    @PostMapping("/comm/api/insertOptLog")
    CommonResult insertOptLog(@RequestBody SysOptLog sysOptLog);

    /**
     * 获取所有数据源
     * @param sysDatasourceDO
     * @return
     */
    @PostMapping("/sys/datasource/listAll")
    CommonResult<List<SysDatasourceDO>> listAllDatasource(@RequestBody SysDatasourceDO sysDatasourceDO);
}
