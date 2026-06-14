package io.github.openground.common.oss;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Oss 基础操作
 * 想要更复杂操作可以直接获取 AmazonS3，通过 AmazonS3 来进行复杂的操作
 * https://docs.aws.amazon.com/zh_cn/sdk-for-java/v1/developer-guide/examples-s3-buckets.html
 *
 * @author open-ground
 */
public interface OssClient {
    /**
     * 创建 bucket
     *
     * @param bucketName bucket 名称
     */
    void createBucket(String bucketName);

    /**
     * 获取全部 bucket
     *
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListBuckets">AWS API Documentation</a>
     */
    List<Bucket> getAllBuckets();

    /**
     * 获取 bucket
     *
     * @param bucketName bucket 名称
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListBuckets">AWS API Documentation</a>
     */
    Optional<Bucket> getBucket(String bucketName);

    /**
     * 删除 bucket
     *
     * @param bucketName bucket 名称
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/DeleteBucket">AWS API Documentation</a>
     */
    void removeBucket(String bucketName);

    /**
     * 根据文件前置查询文件
     *
     * @param bucketName bucket 名称
     * @param prefix     前缀
     * @param recursive  是否递归查询
     * @return S3ObjectSummary 列表
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListObjects">AWS API Documentation</a>
     */
    List<S3ObjectSummary> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive);

    /**
     * 获取 url
     *
     * @param bucketName bucket 名称
     * @param objectName 文件名称
     * @return url
     */
    String getObjectURL(String bucketName, String objectName);

    /**
     * 获取文件外链
     *
     * @param bucketName bucket 名称
     * @param objectName 文件名称
     * @param expires    过期时间，请注意该值必须小于7天
     * @param method     文件操作方法：GET（下载）、PUT（上传）
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration, HttpMethod method)
     */
    String getObjectURL(String bucketName, String objectName, Duration expires, HttpMethod method);

    /**
     * 获取文件
     *
     * @param bucketName bucket 名称
     * @param objectName 文件名称
     * @return 二进制流
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS API Documentation</a>
     */
    S3Object getObject(String bucketName, String objectName);

    /**
     * 上传文件
     *
     * @param bucketName  bucket 名称
     * @param objectName  文件名称
     * @param stream      文件流
     * @param size        文件大小
     * @param contextType 文件类型
     * @param isPub       上传文件是否公有
     * @return 上传结果
     * @throws IOException IO 异常
     */
    PutObjectResult putObject(String bucketName, String objectName, InputStream stream,
                              long size, String contextType, boolean isPub) throws IOException;

    /**
     * 删除文件
     *
     * @param bucketName bucket 名称
     * @param objectName 文件名称
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/DeleteObject">AWS API Documentation</a>
     */
    void removeObject(String bucketName, String objectName);

    /**
     * 默认上传（公有读）
     */
    default PutObjectResult putObject(String bucketName, String objectName, InputStream stream) throws IOException {
        return putObject(bucketName, objectName, stream, stream.available(), "application/octet-stream", true);
    }

    /**
     * 获取原生 S3 客户端
     */
    AmazonS3 getS3Client();
}
