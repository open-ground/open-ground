package io.github.openground.land.dmp.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.DataExchangeConfigDTO;
import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import io.github.openground.land.dmp.mapper.DmpDataExchangeConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据交换配置服务
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Service
public class DataExchangeConfigService {

    @Autowired
    private DmpDataExchangeConfigMapper configMapper;

    public CommonResult<?> list(DataExchangeConfigDTO request) {
        Map<String, Object> param = new HashMap<>();
        param.put("taskName", request.getTaskName());
        param.put("taskType", request.getTaskType());
        param.put("status", request.getStatus());

        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<DmpDataExchangeConfig> list = configMapper.selectList(param);
        PageInfo<DmpDataExchangeConfig> page = new PageInfo<>(list);

        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", page.getList());
        result.put("totalrecord", page.getTotal());
        return CommonResult.success(result);
    }

    public CommonResult<?> getById(String id) {
        DmpDataExchangeConfig config = configMapper.selectById(id);
        if (config == null) {
            return CommonResult.error("1000", "配置不存在");
        }
        return CommonResult.success(config);
    }

    public CommonResult<?> save(DataExchangeConfigDTO request) {
        DmpDataExchangeConfig entity = new DmpDataExchangeConfig();
        BeanUtils.copyProperties(request, entity);
        Date now = new Date();

        if (request.getId() != null && !request.getId().isEmpty()) {
            // 更新
            entity.setUpdateTime(now);
            if (request.getSysHead() != null) {
                entity.setUpdateBy(request.getSysHead().getUserId());
            }
            configMapper.update(entity);
        } else {
            // 新增
            entity.setId(UUID.randomUUID().toString());
            entity.setStatus("ENABLED");
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

    public CommonResult<?> delete(String id) {
        configMapper.deleteById(id);
        return CommonResult.success(null);
    }
}
