package io.github.openground.common.oss;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 本地文件节点 REST API Controller
 * <p>
 * 所有操作委托给 {@link OssClient} 处理。
 * {@link LocalOssClient} 内部会根据节点角色自动判断本地读写或远程转发。
 * </p>
 *
 * @author open-ground
 */
@Slf4j
@RestController
@RequestMapping("/oss")
public class LocalFileController {

    private final OssClient ossClient;

    public LocalFileController(OssClient ossClient) {
        this.ossClient = ossClient;
    }

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
        ossClient.putObject(bucketName, objectName, file.getInputStream(),
                file.getSize(), contentType != null ? contentType : "application/octet-stream", isPub);
        log.debug("文件上传成功: {}/{}", bucketName, objectName);
        return ResponseEntity.ok().build();
    }

    /**
     * 下载文件
     */
    @GetMapping("/file/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectName") String objectName) {
        try {
            S3Object s3Object = ossClient.getObject(bucketName, objectName);
            String contentType = "application/octet-stream";
            if (s3Object.getObjectMetadata() != null
                    && s3Object.getObjectMetadata().getContentType() != null) {
                contentType = s3Object.getObjectMetadata().getContentType();
            }
            long contentLength = 0;
            if (s3Object.getObjectMetadata() != null) {
                contentLength = s3Object.getObjectMetadata().getContentLength();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + objectName + "\"")
                    .body(new InputStreamResource(s3Object.getObjectContent()));
        } catch (Exception e) {
            log.warn("文件下载失败: {}/{}, {}", bucketName, objectName, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/file/delete")
    public ResponseEntity<Void> deleteFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectName") String objectName) {
        try {
            ossClient.removeObject(bucketName, objectName);
            log.debug("文件删除成功: {}/{}", bucketName, objectName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("文件删除失败: {}/{}, {}", bucketName, objectName, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 按前缀查询文件列表
     */
    @GetMapping("/file/list")
    public ResponseEntity<List<S3ObjectSummary>> listFiles(
            @RequestParam("bucketName") String bucketName,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive) {
        List<S3ObjectSummary> summaries = ossClient.getAllObjectsByPrefix(bucketName, prefix, recursive);
        return ResponseEntity.ok(summaries);
    }

    // ==================== Bucket 操作 ====================

    @PostMapping("/bucket/create")
    public ResponseEntity<Void> createBucket(@RequestBody Map<String, String> body) {
        String bucketName = body.get("bucketName");
        if (bucketName == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            ossClient.createBucket(bucketName);
            log.debug("Bucket 创建成功: {}", bucketName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("创建 Bucket 失败: {}", bucketName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/bucket/remove")
    public ResponseEntity<Void> removeBucket(@RequestParam("bucketName") String bucketName) {
        try {
            ossClient.removeBucket(bucketName);
            log.debug("Bucket 删除成功: {}", bucketName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除 Bucket 失败: {}", bucketName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/bucket/list")
    public ResponseEntity<List<Bucket>> listBuckets() {
        List<Bucket> buckets = ossClient.getAllBuckets();
        return ResponseEntity.ok(buckets);
    }
}