package io.github.openground.common.keygen;

import lombok.Data;

import java.io.Serializable;

/**
 * 序列元数据
 * <p>对应数据库表 SYS_AUTO_PMKEY 中的一条记录。</p>
 *
 * @author open-ground
 */
@Data
public class KeyInfoDomain implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键名称（序列名称）
     */
    private String pkName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段名
     */
    private String colId;

    /**
     * 当前最大值
     */
    private long maxValue;

    /**
     * 下一个值
     */
    private long nextKey;

    /**
     * 步长
     */
    private long stepLen;

    /**
     * 主键长度（序号部分的长度）
     */
    private int keyLen;

    /**
     * 前缀
     */
    private String prefix;

    /**
     * 所属系统编码
     */
    private String sysCd;

    /**
     * 重置日期
     */
    private String resetDate;

    /**
     * 重置频率，如 daily / weekly / monthly
     */
    private String resetFreq;

    /**
     * 备注
     */
    private String remark;
}
