package io.github.openground.land.dmp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.dmp.entity.TaskFileDir;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件目录管理 Mapper
 * <p>继承 BaseMapper 获得通用 CRUD，逻辑删除由 @TableLogic 自动处理</p>
 *
 * @author jack.zhang
 * @since 1.0.7
 */
@Mapper
public interface TaskFileDirMapper extends BaseMapper<TaskFileDir> {
}
