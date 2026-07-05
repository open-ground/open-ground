package io.github.openground.common.datasource;

import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.service.SysDatasourceService;
import io.github.openground.common.jdbc.DataSourceDescriptor;
import io.github.openground.common.jdbc.DataSourceProvider;
import io.github.openground.base.utils.AESUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * sys_datasource 表式数据源提供者
 *
 * <p>从 SYS_DATASOURCE 表读取数据源信息，转换为 {@link DataSourceDescriptor}。
 * 优先级 order=20，高于 dblist 配置式 Provider（order=10）。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Component
public class SysDatasourceProvider implements DataSourceProvider {

    @Autowired(required = false)
    private SysDatasourceService sysDatasourceService;

    @Value("${ground.datasource.encrypt.key:ABCDEFG123456KEY}")
    private String encryptKey;

    @Value("${ground.datasource.encrypt.iv:ABCDEFG1234567IV}")
    private String encryptIv;

    @Override
    public List<DataSourceDescriptor> listDataSources() {
        if (sysDatasourceService == null) {
            log.debug("SysDatasourceService not available, returning empty list");
            return Collections.emptyList();
        }
        try {
            List<SysDatasourceDO> list = sysDatasourceService.listAll();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            return list.stream()
                    .map(this::toDescriptor)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("从 sys_datasource 读取数据源失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 将 SysDatasourceDO 转换为 DataSourceDescriptor（密码解密）
     */
    private DataSourceDescriptor toDescriptor(SysDatasourceDO ds) {
        String password = ds.getPassword();
        // 解密密码
        if (password != null && !password.isEmpty()) {
            String decrypted = AESUtil.decrypt(encryptKey, encryptIv, password);
            if (decrypted != null) {
                password = decrypted;
            }
        }
        return DataSourceDescriptor.builder()
                .dsName(ds.getDsName())
                .dbName(ds.getDatabaseName())
                .app(null)
                .url(ds.getJdbcUrl())
                .username(ds.getUsername())
                .password(password)
                .driverClassName(ds.getDriverClassName())
                .dbType(ds.getDbType())
                .source("sys_datasource")
                .rawEntity(ds)
                .build();
    }
}
