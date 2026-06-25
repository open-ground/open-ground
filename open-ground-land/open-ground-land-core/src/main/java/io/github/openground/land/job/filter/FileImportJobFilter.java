package io.github.openground.land.job.filter;

/**
 * 文件导入 Job 过滤器抽象类
 * <p>文件导入场景下的专用过滤器基类，子类实现具体的过滤逻辑。</p>
 *
 * @param <T> 数据类型
 * @author jack.zhang
 * @since 2026-06-25
 */
public abstract class FileImportJobFilter<T> implements JobFilter<T> {

}
