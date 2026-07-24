package io.github.openground.cloud.datasource;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.utils.AESUtil;
import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.jdbc.DataSourceDescriptor;
import io.github.openground.common.jdbc.DataSourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

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
 * @since 1.0.6
 */
@Slf4j
@RequiredArgsConstructor
public class FeignSysDatasourceProvider implements DataSourceProvider {

    private final AuthFeignClient feignClient;

    @Value("${ground.datasource.encrypt.key:ABCDEFG123456KEY}")
    private String encryptKey;

    @Value("${ground.datasource.encrypt.iv:ABCDEFG1234567IV}")
    private String encryptIv;

    @Override
    public List<DataSourceDescriptor> listDataSources() {
        try {
            CommonResult<List<SysDatasourceDO>> result = feignClient.listAllDatasource(new SysDatasourceDO());
            if ("0000".equals(result.getCode())) {
                List<SysDatasourceDO> list = result.getData();
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                return list.stream()
                        .map(this::toDescriptor)
                        .collect(Collectors.toList());
            } else {
                log.error("远程获取数据源异常: code={}, message={}", result.getCode(), result.getMessage());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("从feign远程数据源失败: {}", e.getMessage());
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
