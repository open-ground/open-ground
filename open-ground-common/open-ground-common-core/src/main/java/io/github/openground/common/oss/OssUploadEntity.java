package io.github.openground.common.oss;

import lombok.Data;

/**
 * OSS 上传实体
 *
 * @author open-ground
 */
@Data
public class OssUploadEntity {

    /** 图片的base64数据 */
    private String base64Data;

    /** 是否使用oss */
    private boolean oss;
}
