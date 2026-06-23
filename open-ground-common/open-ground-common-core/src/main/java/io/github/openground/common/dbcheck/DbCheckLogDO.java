package io.github.openground.common.dbcheck;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * DbCheck 操作日志实体类
 *
 * <p>记录数据库检查/同步的操作历史。
 *
 * @author ground-auth
 */
@Getter
@Setter
@Accessors(chain = true)
@ToString
@NoArgsConstructor
@SuppressWarnings("all")
public class DbCheckLogDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 操作类型：check / sync */
    private String operationType;

    /** 数据库类型 */
    private String dbType;

    /** 脚本数量 */
    private Integer scriptCount;

    /** 不一致行数 */
    private Integer mismatchCount;

    /** 多余数据行数 */
    private Integer extraRowCount;

    /** 生成的 SQL 条数 */
    private Integer sqlCount;

    /** 是否已执行同步：0-否 1-是 */
    private String executed;

    /** 操作是否成功：0-失败 1-成功 */
    private String success;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // 查询参数
    private Integer pageNum;
    private Integer pageSize;
}
