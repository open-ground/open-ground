package io.github.openground.land.dmp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.dmp.entity.TaskFileDir;
import io.github.openground.land.dmp.mapper.TaskFileDirMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件目录管理服务
 *
 * @author jack.zhang
 * @since 1.0.7
 */
@Slf4j
@Service
public class TaskFileDirService {

    @Autowired
    private TaskFileDirMapper fileDirMapper;

    /**
     * 分页查询文件目录列表
     */
    public CommonResult<?> list(String dirName, String dirType, int pageIndex, int pageSize) {
        LambdaQueryWrapper<TaskFileDir> wrapper = new LambdaQueryWrapper<>();
        if (dirName != null && !dirName.isEmpty()) {
            wrapper.like(TaskFileDir::getDirName, dirName);
        }
        if (dirType != null && !dirType.isEmpty()) {
            wrapper.eq(TaskFileDir::getDirType, dirType);
        }
        wrapper.orderByDesc(TaskFileDir::getCreateTime);

        PageHelper.startPage(pageIndex, pageSize);
        List<TaskFileDir> list = fileDirMapper.selectList(wrapper);
        PageInfo<TaskFileDir> page = new PageInfo<>(list);

        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", page.getList());
        result.put("totalrecord", page.getTotal());
        return CommonResult.success(result);
    }

    /**
     * 查询全部目录（不分页，供下拉选择用）
     */
    public CommonResult<?> listAll() {
        LambdaQueryWrapper<TaskFileDir> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(TaskFileDir::getDirName);
        List<TaskFileDir> list = fileDirMapper.selectList(wrapper);
        return CommonResult.success(list);
    }

    /**
     * 获取目录详情
     */
    public CommonResult<?> getById(Long id) {
        TaskFileDir dir = fileDirMapper.selectById(id);
        if (dir == null) {
            return CommonResult.error("1000", "目录不存在");
        }
        return CommonResult.success(dir);
    }

    /**
     * 保存目录（新增/更新）
     */
    public CommonResult<?> save(TaskFileDir entity) {
        Date now = new Date();
        if (entity.getId() != null) {
            entity.setUpdateTime(now);
            fileDirMapper.updateById(entity);
        } else {
            entity.setId(KeyGenerator.getInternalKey());
            entity.setDelFlag("0");
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            fileDirMapper.insert(entity);
        }
        return CommonResult.success(null);
    }

    /**
     * 删除目录（逻辑删除）
     */
    public CommonResult<?> delete(Long id) {
        fileDirMapper.deleteById(id);
        return CommonResult.success(null);
    }
}
