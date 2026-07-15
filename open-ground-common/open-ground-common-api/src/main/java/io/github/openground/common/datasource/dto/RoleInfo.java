package io.github.openground.common.datasource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 角色信息 DTO
 *
 * <p>由 {@link io.github.openground.common.datasource.spi.RoleProvider} 提供，
 * 用于数据源表权限管理界面的角色展示与选择。</p>
 *
 * @author open-ground
 * @since 1.0.4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roleId;

    private String roleName;
}
