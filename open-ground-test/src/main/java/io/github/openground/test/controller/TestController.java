package io.github.openground.test.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.log.annotation.OptLog;
import io.github.openground.common.log.enums.OptType;
import io.github.openground.common.security.TokenManager;
import io.github.openground.common.security.spi.UserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用 Controller
 * <p>模拟业务模块的 REST 控制器，展示 open-ground 各组件的使用方式。</p>
 *
 * @author open-ground
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {


    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * 登录接口（无需 Token）
     * <p>根据用户名生成 Token，后续请求需在 Authorization header 中携带此 Token。</p>
     *
     * @param username 用户名
     * @return Token 字符串
     */
    @PostMapping("/login")
    public CommonResult<Map<String, Object>> login(@RequestParam(defaultValue = "admin") String username) {
        String token = tokenManager.generateTokenSso(username);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("tokenType", "Bearer");
        result.put("username", username);
        return CommonResult.success(result);
    }

    /**
     * 获取当前用户信息（需要 Token）
     */
    @GetMapping("/me")
    public CommonResult<Map<String, Object>> me() {
        var user = tokenManager.getCurrentUser();
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("username", user.getUsername());
            result.put("realName", user.getRealName());
        }
        return CommonResult.success(result);
    }

    /**
     * 查询测试接口（GET）
     * 验证 OptLog 注解在 GET 请求上正常工作
     */
    @GetMapping("/hello")
    @OptLog(optType = OptType.QUERY, optRemark = "测试查询接口")
    public CommonResult<Map<String, Object>> hello(@RequestParam(defaultValue = "guest") String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello, " + name + "!");
        data.put("timestamp", System.currentTimeMillis());
        return CommonResult.success(data);
    }

    /**
     * 插入测试接口（POST）
     * 验证 OptLog 注解在 POST 请求 + 请求体上正常工作
     */
    @PostMapping("/user")
    @OptLog(optType = OptType.INSERT, optRemark = "测试新增用户")
    public CommonResult<Map<String, Object>> createUser(@RequestBody Map<String, Object> user) {
        Long userId = KeyGenerator.getInternalKey() ;
        user.put("id", userId);
        log.info("创建用户: {}", user);
        return CommonResult.success(user);
    }

    /**
     * 生成主键序号
     * 验证 KeyGenerator.nextKey 的使用
     */
    @GetMapping("/key/next")
    @OptLog(optType = OptType.QUERY, optRemark = "生成主键序号")
    public CommonResult<Map<String, String>> nextKey(@RequestParam(defaultValue = "TEST_SEQ_01") String seqName) {
        String key = KeyGenerator.getBusinessKey(seqName);
        Map<String, String> result = new HashMap<>();
        result.put("seqName", seqName);
        result.put("key", key);
        return CommonResult.success(result);
    }

    /**
     * 生成业务流水号
     * 验证 KeyGenerator.businessKey 的使用
     */
    @GetMapping("/key/business")
    @OptLog(optType = OptType.QUERY, optRemark = "生成业务流水号")
    public CommonResult<Map<String, String>> businessKey(@RequestParam(defaultValue = "TEST_BUSINESS_KEY") String seqName) {
        String key = KeyGenerator.getBusinessKey(seqName);
        Map<String, String> result = new HashMap<>();
        result.put("seqName", seqName);
        result.put("key", key);
        return CommonResult.success(result);
    }

    /**
     * 生成雪花 ID
     * 验证 KeyGenerator.internalKey 和 IdGenerator 的使用
     */
    @GetMapping("/key/snowflake")
    @OptLog(optType = OptType.QUERY, optRemark = "生成雪花算法 ID")
    public CommonResult<Map<String, Object>> snowflakeId() {
        Long id = KeyGenerator.getInternalKey();
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("type", "snowflake");
        return CommonResult.success(result);
    }

    /**
     * 模拟异常场景
     * 验证 OptLog 在异常时记录错误状态
     */
    @GetMapping("/error")
    @OptLog(optType = OptType.OTHER, optRemark = "测试异常日志")
    public CommonResult<Void> triggerError(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new RuntimeException("模拟业务异常");
        }
        return CommonResult.success();
    }
}
