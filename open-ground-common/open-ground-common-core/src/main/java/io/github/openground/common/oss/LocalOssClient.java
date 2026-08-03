package io.github.openground.common.oss;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件存储 OSS 客户端实现
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>本地模式</b>：当前节点是文件节点时，直接读写本地磁盘</li>
 *   <li><b>远程模式</b>：当前节点不是文件节点时，通过 HTTP 转发到文件节点</li>
 * </ul>
 * </p>
 *
 * @author open-ground
 */
@Slf4j
public class LocalOssClient implements OssClient {

    private static final String FILE_PATH = "/oss/file";
    private static final String BUCKET_PATH = "/oss/bucket";

    private final LocalOssProperties properties;
    private final RestTemplate restTemplate;
    private final boolean isLocalMode;

    public LocalOssClient(LocalOssProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.isLocalMode = isCurrentNodeFileNode();
        log.info("LocalOssClient 初始化完成，模式: {}", isLocalMode ? "本地模式" : "远程模式");
        if (isLocalMode) {
            log.info("文件存储根路径: {}", properties.getPath());
        } else {
            log.info("文件节点地址: {}", properties.getFileNodeUrl());
        }
    }

    // ==================== Bucket 操作 ====================

    @Override
    public void createBucket(String bucketName) {
        if (isLocalMode) {
            localCreateBucket(bucketName);
        } else {
            remoteCreateBucket(bucketName);
        }
    }

    @Override
    public List<Bucket> getAllBuckets() {
        return isLocalMode ? localGetAllBuckets() : remoteGetAllBuckets();
    }

    @Override
    public Optional<Bucket> getBucket(String bucketName) {
        return getAllBuckets().stream()
                .filter(b -> b.getName().equals(bucketName))
                .findFirst();
    }

    @Override
    public void removeBucket(String bucketName) {
        if (isLocalMode) {
            localRemoveBucket(bucketName);
        } else {
            remoteRemoveBucket(bucketName);
        }
    }

    // ==================== 对象操作 ====================

    @Override
    public List<S3ObjectSummary> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive) {
        return isLocalMode
                ? localListObjects(bucketName, prefix, recursive)
                : remoteListObjects(bucketName, prefix, recursive);
    }

    @Override
    public String getObjectURL(String bucketName, String objectName) {
        return buildCurrentNodeUrl(FILE_PATH + "/download?bucketName=" + bucketName
                + "&objectName=" + objectName);
    }

    @Override
    public String getObjectURL(String bucketName, String objectName, Duration expires, com.amazonaws.HttpMethod method) {
        return getObjectURL(bucketName, objectName);
    }

    @Override
    public S3Object getObject(String bucketName, String objectName) {
        return isLocalMode ? localGetObject(bucketName, objectName) : remoteGetObject(bucketName, objectName);
    }

    @Override
    public PutObjectResult putObject(String bucketName, String objectName, InputStream stream,
                                     long size, String contextType, boolean isPub) throws IOException {
        return isLocalMode
                ? localPutObject(bucketName, objectName, stream)
                : remotePutObject(bucketName, objectName, stream, contextType, isPub);
    }

    @Override
    public void removeObject(String bucketName, String objectName) {
        if (isLocalMode) {
            localRemoveObject(bucketName, objectName);
        } else {
            remoteRemoveObject(bucketName, objectName);
        }
    }

    @Override
    public AmazonS3 getS3Client() {
        throw new UnsupportedOperationException("本地文件存储模式不支持 S3 原生客户端操作");
    }

    // ==================== 本地模式实现 ====================

    private void localCreateBucket(String bucketName) {
        try {
            Files.createDirectories(bucketDir(bucketName));
            log.debug("创建 Bucket（目录）: {}", bucketName);
        } catch (IOException e) {
            throw new RuntimeException("创建 Bucket 失败: " + bucketName, e);
        }
    }

    private List<Bucket> localGetAllBuckets() {
        File rootDir = new File(properties.getPath());
        if (!rootDir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] dirs = rootDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(dirs)
                .map(d -> new Bucket(d.getName()))
                .collect(Collectors.toList());
    }

    private void localRemoveBucket(String bucketName) {
        Path dir = bucketDir(bucketName);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("删除 Bucket（目录）: {}", bucketName);
        } catch (IOException e) {
            throw new RuntimeException("删除 Bucket 失败: " + bucketName, e);
        }
    }

    /**
     * 列出 Bucket 下匹配前缀的文件
     * <p>prefix 是相对于 Bucket 的路径前缀，如 "reports/"、"uploads/2026-07-31/"。
     * 匹配时比较的是文件的相对路径（相对于 Bucket 目录），而非仅文件名。</p>
     */
    private List<S3ObjectSummary> localListObjects(String bucketName, String prefix, boolean recursive) {
        Path bucketDir = bucketDir(bucketName);
        if (!Files.isDirectory(bucketDir)) {
            return Collections.emptyList();
        }

        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(bucketDir, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> matchesPrefix(bucketDir, p, prefix))
                    .map(p -> toSummary(bucketName, bucketDir, p))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("列出文件失败 bucket={}, prefix={}", bucketName, prefix, e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断文件相对路径是否匹配前缀
     * <p>相对路径 = bucketDir 到文件的相对路径，如 "reports/2026-07-31/zhangs/file.md"。
     * prefix 为 "reports/" 时，匹配所有以 "reports/" 开头的文件。</p>
     */
    private boolean matchesPrefix(Path bucketDir, Path file, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        String relativePath = bucketDir.relativize(file).toString().replace("\\", "/");
        return relativePath.startsWith(prefix);
    }

    private S3ObjectSummary toSummary(String bucketName, Path bucketDir, Path file) {
        S3ObjectSummary summary = new S3ObjectSummary();
        summary.setBucketName(bucketName);
        summary.setKey(bucketDir.relativize(file).toString().replace("\\", "/"));
        try {
            summary.setSize(Files.size(file));
            summary.setLastModified(new Date(Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException ignored) {
        }
        return summary;
    }

    private S3Object localGetObject(String bucketName, String objectName) {
        Path filePath = objectPath(bucketName, objectName);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("文件不存在: " + bucketName + "/" + objectName);
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(filePath));
            metadata.setContentType(Files.probeContentType(filePath));
            metadata.setLastModified(new Date(Files.getLastModifiedTime(filePath).toMillis()));

            S3Object s3Object = new S3Object();
            s3Object.setBucketName(bucketName);
            s3Object.setKey(objectName);
            s3Object.setObjectMetadata(metadata);
            s3Object.setObjectContent(new S3ObjectInputStream(
                    Files.newInputStream(filePath), (org.apache.http.client.methods.HttpRequestBase) null));
            return s3Object;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + bucketName + "/" + objectName, e);
        }
    }

    private PutObjectResult localPutObject(String bucketName, String objectName, InputStream stream) throws IOException {
        Path bucketDir = bucketDir(bucketName);
        Files.createDirectories(bucketDir);
        Path filePath = bucketDir.resolve(objectName);
        Files.createDirectories(filePath.getParent());

        try (InputStream in = stream) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.debug("上传文件: {} ({})", bucketName + "/" + objectName, filePath);
        PutObjectResult result = new PutObjectResult();
        result.setETag(objectName);
        return result;
    }

    private void localRemoveObject(String bucketName, String objectName) {
        try {
            Files.deleteIfExists(objectPath(bucketName, objectName));
            log.debug("删除文件: {}/{}", bucketName, objectName);
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败: " + bucketName + "/" + objectName, e);
        }
    }

    // ==================== 远程模式实现 ====================

    private void remoteCreateBucket(String bucketName) {
        String url = buildUrl(BUCKET_PATH + "/create");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new HashMap<>();
        body.put("bucketName", bucketName);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
    }

    private List<Bucket> remoteGetAllBuckets() {
        String url = buildUrl(BUCKET_PATH + "/list");
        Bucket[] buckets = restTemplate.getForEntity(url, Bucket[].class).getBody();
        return buckets != null ? Arrays.asList(buckets) : Collections.emptyList();
    }

    private void remoteRemoveBucket(String bucketName) {
        restTemplate.exchange(buildUrl(BUCKET_PATH + "/remove?bucketName=" + bucketName),
                org.springframework.http.HttpMethod.DELETE, null, Void.class);
    }

    private List<S3ObjectSummary> remoteListObjects(String bucketName, String prefix, boolean recursive) {
        String url = buildUrl(FILE_PATH + "/list?bucketName=" + bucketName
                + "&prefix=" + (prefix != null ? prefix : "")
                + "&recursive=" + recursive);
        S3ObjectSummary[] summaries = restTemplate.getForEntity(url, S3ObjectSummary[].class).getBody();
        return summaries != null ? Arrays.asList(summaries) : Collections.emptyList();
    }

    private S3Object remoteGetObject(String bucketName, String objectName) {
        String url = buildUrl(FILE_PATH + "/download?bucketName=" + bucketName
                + "&objectName=" + objectName);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, null, byte[].class);
        byte[] data = response.getBody() != null ? response.getBody() : new byte[0];

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null) {
            metadata.setContentType(contentType.toString());
        }

        S3Object s3Object = new S3Object();
        s3Object.setBucketName(bucketName);
        s3Object.setKey(objectName);
        s3Object.setObjectMetadata(metadata);
        s3Object.setObjectContent(new S3ObjectInputStream(
                new ByteArrayInputStream(data), (org.apache.http.client.methods.HttpRequestBase) null));
        return s3Object;
    }

    private PutObjectResult remotePutObject(String bucketName, String objectName, InputStream stream,
                                            String contextType, boolean isPub) throws IOException {
        String url = buildUrl(FILE_PATH + "/upload");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // JDK 1.8 没有 InputStream.readAllBytes()（JDK 9+），手动读取全部字节
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        ByteArrayResource fileResource = new ByteArrayResource(buffer.toByteArray()) {
            @Override
            public String getFilename() {
                return objectName;
            }
        };
        body.add("file", fileResource);
        body.add("bucketName", bucketName);
        body.add("objectName", objectName);
        body.add("contentType", contextType);
        body.add("isPub", String.valueOf(isPub));

        ResponseEntity<PutObjectResult> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), PutObjectResult.class);
        return response.getBody() != null ? response.getBody() : new PutObjectResult();
    }

    private void remoteRemoveObject(String bucketName, String objectName) {
        restTemplate.exchange(buildUrl(FILE_PATH + "/delete?bucketName=" + bucketName
                        + "&objectName=" + objectName),
                org.springframework.http.HttpMethod.DELETE, null, Void.class);
    }

    // ==================== 内部方法 ====================

    private boolean isCurrentNodeFileNode() {
        String fileNodeUrl = properties.getFileNodeUrl();
        if (fileNodeUrl == null || fileNodeUrl.isEmpty()) {
            return true;
        }
        try {
            URI uri = new URI(fileNodeUrl);
            InetAddress fileNodeAddress = InetAddress.getByName(uri.getHost());
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(iface -> Collections.list(iface.getInetAddresses()).stream())
                    .anyMatch(addr -> addr.equals(fileNodeAddress));
        } catch (Exception e) {
            log.warn("判断文件节点失败，默认使用本地模式: {}", e.getMessage());
            return true;
        }
    }

    private Path bucketDir(String bucketName) {
        return Paths.get(properties.getPath(), bucketName);
    }

    private Path objectPath(String bucketName, String objectName) {
        return bucketDir(bucketName).resolve(objectName);
    }

    private String buildUrl(String path) {
        String baseUrl = properties.getFileNodeUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private String buildCurrentNodeUrl(String path) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + request.getContextPath() + path;
        } catch (Exception e) {
            return path;
        }
    }
}