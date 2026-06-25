package io.github.openground.land.common.dao;

import java.io.Serializable;

/**
 * 基础持久化对象，替代原 BasePo。
 * 仅保留 Serializable 能力，实体类继承关系保持不变。
 *
 * @author jack.zhang
 */
public abstract class BasePo implements Serializable {

    private static final long serialVersionUID = 1L;
}
