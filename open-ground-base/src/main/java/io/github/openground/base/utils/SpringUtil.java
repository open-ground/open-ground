package io.github.openground.base.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 工具类：方便在非 Spring 管理环境中获取 Bean
 *
 * @author open-ground
 * @version 1.0
 */
@Component
public final class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    /**
     * 获取对象
     *
     * @param name Bean 名称
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }

    /**
     * 获取类型为 requiredType 的对象
     *
     * @param clz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clz) {
        return applicationContext.getBean(clz);
    }

    /**
     * 如果 applicationContext 包含一个与所给名称匹配的 bean 定义，则返回 true
     *
     * @param name Bean 名称
     * @return 是否包含
     */
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }

    /**
     * 判断以给定名字注册的 bean 定义是一个 singleton 还是一个 prototype
     *
     * @param name Bean 名称
     * @return true=singleton, false=prototype
     * @throws NoSuchBeanDefinitionException 未找到异常
     */
    public static boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        return applicationContext.isSingleton(name);
    }

    /**
     * 获取注册对象的类型
     *
     * @param name Bean 名称
     * @return 注册对象的类型
     */
    public static Class<?> getType(String name) {
        return applicationContext.getType(name);
    }

    /**
     * 获取 Bean 的所有别名
     *
     * @param name Bean 名称
     * @return 别名数组
     */
    public static String[] getAliases(String name) {
        return applicationContext.getAliases(name);
    }

    /**
     * 获取 ApplicationContext
     *
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
