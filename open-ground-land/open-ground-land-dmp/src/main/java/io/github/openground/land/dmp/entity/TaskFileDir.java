package io.github.openground.land.dmp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;

import java.util.Date;

/**
 * 文件目录管理实体
 * <p>对应表 TASK_DATA_FILE_DIR，统一管理数据交换中的文件目录路径</p>
 *
 * @author jack.zhang
 * @since 1.0.7
 */
@Data
@TableName("TASK_DATA_FILE_DIR")
public class TaskFileDir extends BasePo {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 目录名称（便于识别，如：数据导入目录、导出目录） */
    private String dirName;

    /** 目录路径（如：/data/files/input） */
    private String dirPath;

    /** 目录类型：LOCAL-本地 SFTP-远程SFTP FTP-远程FTP */
    private String dirType;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private Date updateTime;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
