package io.github.openground.common.auth;

import io.github.openground.base.dto.CommonResult;

import java.util.List;

/**
 * 通用权限服务
 *
 * @author open-ground
 * @since 1.0.0
 */
public interface AuthCommonProvider {

    /**
     * 获取用户权限
     *
     * @param userName 用户名
     * @return 用户权限
     */
    CommonResult<?> getuserFuncs(String userName);

    /**
     * 获取用户信息
     *
     * @param userName 用户名
     * @return 用户信息
     */
    CommonResult<?> getUserInfoByUserName(String userName);

    /**
     * 批量获取用户信息
     *
     * @param userNames 用户名
     * @return 用户信息
     */
    CommonResult<?> getUsersByUserNames(List<String> userNames);

    /**
     * 获取字典信息
     *
     * @param dictType 字典类型
     * @param appName  应用名称
     * @return 字典
     */
    CommonResult<?> getDictByType(String dictType, String appName);

    /**
     * 获取所有机构信息
     *
     * @return 机构信息
     */
    CommonResult<?> listAllOrg();



}
