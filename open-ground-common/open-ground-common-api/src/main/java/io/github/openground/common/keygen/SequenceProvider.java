package io.github.openground.common.keygen;

/**
 * 序列提供者 SPI 接口
 * <p>负责从持久化存储中获取并推进序列值，由不同部署模式提供不同实现：</p>
 * <ul>
 *   <li>集成部署 → {@code JdbcSequenceProvider}（JdbcTemplate 直连数据库）</li>
 *   <li>分离部署 → {@code FeignSequenceProvider}（远程 Feign 调用 Auth 服务）</li>
 * </ul>
 *
 * @author open-ground
 */
@FunctionalInterface
public interface SequenceProvider {

    /**
     * 获取序列并推进
     *
     * @param keyName 序列名称（对应 SYS_AUTO_PMKEY.PK_NAME）
     * @param count   预取数量
     * @return KeyInfoDomain 包含当前最大值、步长、长度、前缀等信息
     */
    KeyInfoDomain retrieveAndAdvance(String keyName, int count);
}
