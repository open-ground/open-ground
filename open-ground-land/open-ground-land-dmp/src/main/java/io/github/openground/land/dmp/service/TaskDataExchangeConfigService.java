package io.github.openground.land.dmp.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.api.dto.DataExchangeConfigDTO;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import io.github.openground.land.dmp.mapper.TaskDataExchangeConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据交换配置服务
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@Slf4j
@Service
public class TaskDataExchangeConfigService {

    @Autowired
    private TaskDataExchangeConfigMapper configMapper;

    public CommonResult<?> list(DataExchangeConfigDTO request) {
        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<TaskDataExchangeConfig> list = configMapper.selectList(
                request.getTaskName(), request.getTaskType(), request.getTaskStatus(), null);
        PageInfo<TaskDataExchangeConfig> page = new PageInfo<>(list);

        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", page.getList());
        result.put("totalrecord", page.getTotal());
        return CommonResult.success(result);
    }

    public CommonResult<?> getById(Long id) {
        TaskDataExchangeConfig config = configMapper.selectById(id);
        if (config == null) {
            return CommonResult.error("1000", "配置不存在");
        }
        return CommonResult.success(config);
    }

    public CommonResult<?> save(DataExchangeConfigDTO request) {
        TaskDataExchangeConfig entity = new TaskDataExchangeConfig();
        BeanUtils.copyProperties(request, entity);
        Date now = new Date();

        if (request.getId() != null) {
            // 更新
            entity.setUpdateTime(now);
            if (request.getSysHead() != null) {
                entity.setUpdateBy(request.getSysHead().getUserId());
            }
            configMapper.updateById(entity);
        } else {
            // 新增
            entity.setId(KeyGenerator.getInternalKey());
            entity.setTaskStatus("ENABLED");
            entity.setDelFlag("0");
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            if (request.getSysHead() != null) {
                entity.setCreateBy(request.getSysHead().getUserId());
                entity.setUpdateBy(request.getSysHead().getUserId());
            }
            configMapper.insert(entity);
        }
        return CommonResult.success(null);
    }

    public CommonResult<?> delete(Long id) {
        configMapper.deleteById(id);
        return CommonResult.success(null);
    }
}
