package io.github.openground.land.dmp.service;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.mapper.SysDatasourceMapper;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import io.github.openground.land.dmp.entity.TaskFileDir;
import io.github.openground.land.dmp.mapper.TaskDataExchangeConfigMapper;
import io.github.openground.land.dmp.mapper.TaskFileDirMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据流服务
 * <p>根据数据交换配置组装数据流图数据（nodes + edges）</p>
 *
 * @author jack.zhang
 * @since 1.0.7
 */
@Slf4j
@Service
public class LineageService {

    @Autowired
    private TaskDataExchangeConfigMapper configMapper;

    @Autowired
    private SysDatasourceMapper sysDatasourceMapper;

    @Autowired
    private TaskFileDirMapper fileDirMapper;

    /**
     * 获取数据数据流图数据
     *
     * @return { nodes: [...], edges: [...] }
     */
    public CommonResult<?> getLineage() {
        // 查询所有启用的配置
        List<TaskDataExchangeConfig> configs = configMapper.selectEnabledConfigs();

        // 缓存数据源名称
        Map<Long, String> dsNameCache = new HashMap<>();
        // 缓存文件目录路径
        Map<Long, String> dirPathCache = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, String> nodeIdMap = new HashMap<>(); // 去重节点

        for (TaskDataExchangeConfig config : configs) {
            String taskType = config.getTaskType();
            String taskName = config.getTaskName();

            String sourceNodeId = null;
            String targetNodeId = null;

            if ("FILE_TO_DB".equals(taskType)) {
                // 文件 -> 表
                sourceNodeId = buildFileNode(config, dirPathCache, nodes, nodeIdMap);
                targetNodeId = buildTableNode(config.getTargetDsId(), config.getTargetTable(), dsNameCache, nodes, nodeIdMap);
            } else if ("DB_TO_FILE".equals(taskType)) {
                // 表 -> 文件
                sourceNodeId = buildTableNode(config.getSourceDsId(), config.getTargetTable(), dsNameCache, nodes, nodeIdMap);
                targetNodeId = buildFileNode(config, dirPathCache, nodes, nodeIdMap);
            } else if ("DB_TO_DB".equals(taskType)) {
                // 表 -> 表
                sourceNodeId = buildTableNode(config.getSourceDsId(), config.getSourceTable(), dsNameCache, nodes, nodeIdMap);
                targetNodeId = buildTableNode(config.getTargetDsId(), config.getTargetTable(), dsNameCache, nodes, nodeIdMap);
            }

            if (sourceNodeId != null && targetNodeId != null) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", sourceNodeId);
                edge.put("target", targetNodeId);
                edge.put("label", taskName);
                edge.put("taskType", taskType);
                edges.add(edge);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return CommonResult.success(result);
    }

    /**
     * 构建文件节点，返回节点ID。已存在则复用。
     */
    private String buildFileNode(TaskDataExchangeConfig config, Map<Long, String> dirPathCache,
                                  List<Map<String, Object>> nodes, Map<String, String> nodeIdMap) {
        String fileName = config.getSourceFilePath() != null ? config.getSourceFilePath() : config.getTargetFilePath();
        if (fileName == null) fileName = "unknown";

        // 获取目录路径（如果有）
        Long dirId = config.getSourceFileDirId() != null ? config.getSourceFileDirId() : config.getTargetFileDirId();
        String dirPath = null;
        if (dirId != null) {
            dirPath = dirPathCache.computeIfAbsent(dirId, id -> {
                TaskFileDir dir = fileDirMapper.selectById(id);
                return dir != null ? dir.getDirPath() : null;
            });
        }

        // 节点标签：[来源系统] 文件名  或  目录/文件名
        String label;
        String sourceSystem = config.getSourceSystem();
        if (sourceSystem != null && !sourceSystem.isEmpty()) {
            label = "[" + sourceSystem + "] " + fileName;
        } else if (dirPath != null) {
            label = dirPath + "/" + fileName;
        } else {
            label = fileName;
        }

        // 节点ID用文件名+dirId去重
        String nodeId = "file_" + (dirId != null ? dirId : "nodir") + "_" + fileName;
        if (!nodeIdMap.containsKey(nodeId)) {
            nodeIdMap.put(nodeId, nodeId);
            Map<String, Object> node = new HashMap<>();
            node.put("id", nodeId);
            node.put("label", label);
            node.put("nodeType", "FILE");
            node.put("sourceSystem", sourceSystem);
            nodes.add(node);
        }
        return nodeId;
    }

    /**
     * 构建表节点，返回节点ID。已存在则复用。
     */
    private String buildTableNode(Long dsId, String tableName, Map<Long, String> dsNameCache,
                                   List<Map<String, Object>> nodes, Map<String, String> nodeIdMap) {
        if (dsId == null || tableName == null) return null;

        String dsName = dsNameCache.computeIfAbsent(dsId, id -> {
            SysDatasourceDO ds = sysDatasourceMapper.selectById(id);
            return ds != null ? ds.getDsName() : String.valueOf(id);
        });

        String nodeId = "table_" + dsId + "_" + tableName;
        if (!nodeIdMap.containsKey(nodeId)) {
            nodeIdMap.put(nodeId, nodeId);
            Map<String, Object> node = new HashMap<>();
            node.put("id", nodeId);
            node.put("label", dsName + "." + tableName);
            node.put("nodeType", "TABLE");
            node.put("dsName", dsName);
            node.put("tableName", tableName);
            nodes.add(node);
        }
        return nodeId;
    }
}
