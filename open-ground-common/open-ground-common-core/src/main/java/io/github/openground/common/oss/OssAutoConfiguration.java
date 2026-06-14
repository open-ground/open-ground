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

import java.util.Objects;
import java.util.stream.Stream;

/**
 * OSS 自动配置
 * <p>
 * 通过 {@code ground.oss.enable=true} 启用。
 * 参考文档：
 * <a href="https://docs.aws.amazon.com/zh_cn/sdk-for-java/v1/developer-guide/credentials.html">Credentials</a>
 * <a href="https://docs.aws.amazon.com/zh_cn/sdk-for-java/v1/developer-guide/java-dg-region-selection.html">Region</a>
 * </p>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AmazonS3.class)
    @ConditionalOnProperty(prefix = "ground.oss", name = "enable", havingValue = "true")
    public OssClient ossClient(AmazonS3 amazonS3) {
        log.info("初始化 OssClient（S3协议）");
        return new S3OssClient(amazonS3);
    }

    @Bean
    @ConditionalOnMissingBean(AmazonS3.class)
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
}
