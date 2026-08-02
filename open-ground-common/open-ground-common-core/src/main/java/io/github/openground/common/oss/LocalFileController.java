package io.github.openground.common.oss;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件节点 REST API Controller
 * <p>
 * 仅在当前节点是文件节点时注册，提供文件上传/下载/删除等操作。
 * 其他节点通过 {@link LocalOssClient} 远程调用此 Controller。
 * </p>
 *
 * @author open-ground
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/oss")
public class LocalFileController {

    private final LocalOssProperties localOssProperties;

    // ==================== 文件操作 ====================

    /**
     * 上传文件
     */
    @PostMapping("/file/upload")
    public ResponseEntity<Void> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "isPub", defaultValue = "true") boolean isPub) throws IOException {
        Path bucketDir = Paths.get(localOssProperties.getPath(), bucketName);
        Files.createDirectories(bucketDir);
        Path filePath = bucketDir.resolve(objectName);
        Files.createDirectories(filePath.getParent());
        file.transferTo(filePath.toFile());
        log.debug("文件节点上传文件: {} ({} bytes)", filePath, file.getSize());
        return ResponseEntity.ok().build();
    }

    /**
     * 下载文件
     */
    @GetMapping("/file/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectName") String objectName) throws IOException {
        Path filePath = Paths.get(localOssProperties.getPath(), bucketName, objectName);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        File file = filePath.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/file/delete")
    public ResponseEntity<Void> deleteFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectName") String objectName) throws IOException {
        Path filePath = Paths.get(localOssProperties.getPath(), bucketName, objectName);
        Files.deleteIfExists(filePath);
        log.debug("文件节点删除文件: {}", filePath);
        return ResponseEntity.ok().build();
    }

    /**
     * 按前缀查询文件列表
     */
    @GetMapping("/file/list")
    public ResponseEntity<List<S3ObjectSummary>> listFiles(
            @RequestParam("bucketName") String bucketName,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive) {
        Path bucketDir = Paths.get(localOssProperties.getPath(), bucketName);
        if (!Files.exists(bucketDir)) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        try {
            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            try (Stream<Path> pathStream = Files.walk(bucketDir, maxDepth)) {
                List<S3ObjectSummary> summaries = pathStream
                        .filter(Files::isRegularFile)
                        .filter(p -> matchesPrefix(bucketDir, p, prefix))
                        .map(p -> toSummary(bucketName, bucketDir, p))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(summaries);
            }
        } catch (IOException e) {
            log.error("列出文件失败", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * 判断文件相对路径是否匹配前缀
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

    // ==================== Bucket 操作 ====================

    /**
     * 创建 Bucket（目录）
     */
    @PostMapping("/bucket/create")
    public ResponseEntity<Void> createBucket(@RequestBody Map<String, String> body) {
        String bucketName = body.get("bucketName");
        if (bucketName == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Files.createDirectories(Paths.get(localOssProperties.getPath(), bucketName));
            log.debug("文件节点创建 Bucket: {}", bucketName);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("创建 Bucket 失败: {}", bucketName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除 Bucket（目录）
     */
    @DeleteMapping("/bucket/remove")
    public ResponseEntity<Void> removeBucket(@RequestParam("bucketName") String bucketName) {
        Path bucketDir = Paths.get(localOssProperties.getPath(), bucketName);
        if (Files.exists(bucketDir)) {
            try {
                Files.walk(bucketDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.debug("文件节点删除 Bucket: {}", bucketName);
            } catch (IOException e) {
                log.error("删除 Bucket 失败: {}", bucketName, e);
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 列出所有 Bucket
     */
    @GetMapping("/bucket/list")
    public ResponseEntity<List<Bucket>> listBuckets() {
        File rootDir = new File(localOssProperties.getPath());
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        File[] dirs = rootDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<Bucket> buckets = Arrays.stream(dirs)
                .map(d -> new Bucket(d.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(buckets);
    }
}