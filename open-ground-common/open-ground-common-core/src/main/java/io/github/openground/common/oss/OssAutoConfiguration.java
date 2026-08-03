package io.github.openground.common.oss;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * OSS 自动配置
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>本地模式</b>（默认）：不配置或 {@code ground.oss.type=local}，使用本地磁盘存储</li>
 *   <li><b>S3 模式</b>：需要 {@code ground.oss.type=s3} 且 {@code ground.oss.enable=true}，使用 AWS S3 协议</li>
 * </ul>
 * </p>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({OssProperties.class, LocalOssProperties.class})
public class OssAutoConfiguration {

    // ==================== S3 模式 ====================

    @Bean
    @ConditionalOnMissingBean(OssClient.class)
    @ConditionalOnProperty(prefix = "ground.oss", name = "type", havingValue = "s3")
    @ConditionalOnProperty(prefix = "ground.oss", name = "enable", havingValue = "true")
    public OssClient s3OssClient(AmazonS3 amazonS3) {
        log.info("初始化 OssClient（S3协议）");
        return new S3OssClient(amazonS3);
    }

    @Bean
    @ConditionalOnMissingBean(AmazonS3.class)
    @ConditionalOnProperty(prefix = "ground.oss", name = "type", havingValue = "s3")
    @ConditionalOnProperty(prefix = "ground.oss", name = "enable", havingValue = "true")
    public AmazonS3 amazonS3(OssProperties ossProperties) {
        long nullSize = Stream.<String>builder()
                .add(ossProperties.getEndpoint())
                .add(ossProperties.getAccessSecret())
                .add(ossProperties.getAccessKey())
                .build()
                .filter(Objects::isNull)
                .count();
        if (nullSize > 0) {
            throw new RuntimeException("oss 配置错误,请检查");
        }
        AWSCredentials awsCredentials = new BasicAWSCredentials(
                ossProperties.getAccessKey(), ossProperties.getAccessSecret());
        AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        return AmazonS3Client.builder()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        ossProperties.getEndpoint(), ossProperties.getRegion()))
                .withCredentials(awsCredentialsProvider)
                .disableChunkedEncoding()
                .withPathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                .build();
    }

    // ==================== 本地文件模式（默认）====================

    @Bean
    @ConditionalOnMissingBean(OssClient.class)
    @ConditionalOnProperty(prefix = "ground.oss", name = "type", havingValue = "local", matchIfMissing = true)
    public OssClient localOssClient(LocalOssProperties localOssProperties) {
        log.info("初始化 OssClient（本地文件模式）");
        RestTemplate restTemplate = new RestTemplate();
        return new LocalOssClient(localOssProperties, restTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ground.oss", name = "type", havingValue = "local", matchIfMissing = true)
    public LocalFileController localFileController(OssClient localOssClient) {
        return new LocalFileController(localOssClient);
    }
}