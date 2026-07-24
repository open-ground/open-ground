package io.github.openground.common.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.openground.test.TestApplication;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicJdbcTemplate 集成测试
 *
 * <p>基于真实 MySQL 数据源（dsName=auth）测试 DynamicJdbcTemplate 的核心能力：
 * 参数化查询、参数化更新、DDL 执行、批量操作、原始 SQL、事务（提交+回滚）、工具方法。
 *
 * <p>测试表：sys_login_log（需提前在 auth 数据源中创建）。
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = TestApplication.class)
@DisplayName("DynamicJdbcTemplate 集成测试")
class DynamicJdbcTemplateTest {

    private static final String DS_NAME = "auth";
    private static final String TABLE = "sys_login_log";

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private DynamicDataSourceManager dataSourceManager;

    /** 事务模板，绑定 auth 数据源，用于事务测试 */
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        dynamicJdbcTemplate.executeDdl(DS_NAME, "DELETE FROM " + TABLE);
        // 基于 auth 数据源创建事务管理器和事务模板
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(
                dataSourceManager.getDataSource(DS_NAME));
        transactionTemplate = new TransactionTemplate(txManager);
    }

    @AfterEach
    void tearDown() {
        dynamicJdbcTemplate.executeDdl(DS_NAME, "DELETE FROM " + TABLE);
    }

    /** 辅助方法：插入一条测试记录 */
    private void insertLog(String logId, String userId, String ip) {
        Map<String, Object> params = new HashMap<>();
        params.put("logId", logId);
        params.put("userId", userId);
        params.put("ip", ip);
        dynamicJdbcTemplate.update(DS_NAME,
                "INSERT INTO " + TABLE + " (LOG_ID, USER_ID, IP_ADDRESS) VALUES (:logId, :userId, :ip)", params);
    }

    // ==================== 参数化查询 ====================

    @Nested
    @DisplayName("queryForList - 参数化查询")
    class QueryForListTest {

        @Test
        @DisplayName("无参数查询返回全部行")
        void shouldQueryAllWithoutParams() {
            insertLog("1001", "userA", "127.0.0.1");
            insertLog("1002", "userB", "192.168.1.1");

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " ORDER BY LOG_ID");

            assertEquals(2, result.size());
            assertEquals("1001", result.get(0).get("LOG_ID"));
            assertEquals("1002", result.get(1).get("LOG_ID"));
        }

        @Test
        @DisplayName("带参数查询返回匹配行")
        void shouldQueryWithParams() {
            insertLog("2001", "userA", "127.0.0.1");
            insertLog("2002", "userB", "192.168.1.1");

            Map<String, Object> params = new HashMap<>();
            params.put("userId", "userA");
            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = :userId", params);

            assertEquals(1, result.size());
            assertEquals("2001", result.get(0).get("LOG_ID"));
        }

        @Test
        @DisplayName("空表查询返回空列表")
        void shouldReturnEmptyListWhenNoData() {
            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("queryForOne - 查询单条记录")
    class QueryForOneTest {

        @Test
        @DisplayName("查询单条记录返回 Map")
        void shouldReturnSingleRecord() {
            insertLog("3001", "userC", "10.0.0.1");

            Map<String, Object> params = new HashMap<>();
            params.put("logId", "3001");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", params);

            assertNotNull(result);
            assertEquals("3001", result.get("LOG_ID"));
            assertEquals("userC", result.get("USER_ID"));
        }

        @Test
        @DisplayName("无匹配记录时返回 null")
        void shouldReturnNullWhenNotFound() {
            Map<String, Object> params = new HashMap<>();
            params.put("logId", "not_exist");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", params);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("queryForObject - 查询单个值")
    class QueryForObjectTest {

        @Test
        @DisplayName("查询 COUNT 返回 Integer")
        void shouldReturnCount() {
            insertLog("4001", "userD", "127.0.0.1");
            insertLog("4002", "userD", "127.0.0.2");

            Map<String, Object> params = new HashMap<>();
            params.put("userId", "userD");
            Integer count = dynamicJdbcTemplate.queryForObject(DS_NAME,
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE USER_ID = :userId", params, Integer.class);

            assertNotNull(count);
            assertEquals(2, count.intValue());
        }
    }

    // ==================== 参数化更新 ====================

    @Nested
    @DisplayName("update - 参数化 INSERT/UPDATE/DELETE")
    class UpdateTest {

        @Test
        @DisplayName("INSERT 插入数据并验证")
        void shouldInsertWithParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("logId", "5001");
            params.put("userId", "userE");
            params.put("ip", "172.16.0.1");

            int rows = dynamicJdbcTemplate.update(DS_NAME,
                    "INSERT INTO " + TABLE + " (LOG_ID, USER_ID, IP_ADDRESS) VALUES (:logId, :userId, :ip)", params);

            assertEquals(1, rows);

            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("logId", "5001");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", queryParams);
            assertNotNull(result);
            assertEquals("userE", result.get("USER_ID"));
        }

        @Test
        @DisplayName("UPDATE 修改数据并验证")
        void shouldUpdateWithParams() {
            insertLog("6001", "userF", "127.0.0.1");

            Map<String, Object> params = new HashMap<>();
            params.put("ip", "10.20.30.40");
            params.put("logId", "6001");
            int rows = dynamicJdbcTemplate.update(DS_NAME,
                    "UPDATE " + TABLE + " SET IP_ADDRESS = :ip WHERE LOG_ID = :logId", params);

            assertEquals(1, rows);

            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("logId", "6001");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", queryParams);
            assertEquals("10.20.30.40", result.get("IP_ADDRESS"));
        }

        @Test
        @DisplayName("DELETE 删除数据并验证")
        void shouldDeleteWithParams() {
            insertLog("7001", "userG", "127.0.0.1");
            insertLog("7002", "userG", "127.0.0.2");

            Map<String, Object> params = new HashMap<>();
            params.put("userId", "userG");
            int rows = dynamicJdbcTemplate.update(DS_NAME,
                    "DELETE FROM " + TABLE + " WHERE USER_ID = :userId", params);

            assertEquals(2, rows);

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = 'userG'");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("executeUpdateWithPs - PreparedStatement 直接执行")
    class ExecuteUpdateWithPsTest {

        @Test
        @DisplayName("使用 PreparedStatement 执行 INSERT")
        void shouldInsertWithPreparedStatement() {
            int rows = dynamicJdbcTemplate.executeUpdateWithPs(DS_NAME,
                    "INSERT INTO " + TABLE + " (LOG_ID, USER_ID, IP_ADDRESS, LOGIN_STATUS) VALUES (?, ?, ?, ?)",
                    Arrays.asList("8001", "userH", "127.0.0.1", "1"));

            assertEquals(1, rows);

            Map<String, Object> params = new HashMap<>();
            params.put("logId", "8001");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", params);
            assertNotNull(result);
            assertEquals("1", result.get("LOGIN_STATUS"));
        }

        @Test
        @DisplayName("使用 PreparedStatement 执行 UPDATE")
        void shouldUpdateWithPreparedStatement() {
            insertLog("8002", "userI", "127.0.0.1");

            int rows = dynamicJdbcTemplate.executeUpdateWithPs(DS_NAME,
                    "UPDATE " + TABLE + " SET LOGIN_STATUS = ? WHERE LOG_ID = ?",
                    Arrays.asList("0", "8002"));

            assertEquals(1, rows);

            Map<String, Object> params = new HashMap<>();
            params.put("logId", "8002");
            Map<String, Object> result = dynamicJdbcTemplate.queryForOne(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = :logId", params);
            assertEquals("0", result.get("LOGIN_STATUS"));
        }
    }

    // ==================== DDL 执行 ====================

    @Nested
    @DisplayName("executeDdl - DDL 语句执行")
    class ExecuteDdlTest {

        @Test
        @DisplayName("executeDdl 执行 DELETE 清理数据")
        void shouldExecuteDeleteAsDdl() {
            insertLog("9001", "userJ", "127.0.0.1");
            insertLog("9002", "userJ", "127.0.0.2");

            assertDoesNotThrow(() ->
                    dynamicJdbcTemplate.executeDdl(DS_NAME, "DELETE FROM " + TABLE + " WHERE USER_ID = 'userJ'"));

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = 'userJ'");
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 批量操作 ====================

    @Nested
    @DisplayName("batchUpdate - 批量更新")
    class BatchUpdateTest {

        @Test
        @DisplayName("批量插入 3 条数据")
        @SuppressWarnings("unchecked")
        void shouldBatchInsert() {
            String sql = "INSERT INTO " + TABLE +
                    " (LOG_ID, USER_ID, IP_ADDRESS) VALUES (:logId, :userId, :ip)";

            Map<String, Object>[] batchParams = new Map[3];
            for (int i = 0; i < 3; i++) {
                batchParams[i] = new HashMap<>();
                batchParams[i].put("logId", "B" + i);
                batchParams[i].put("userId", "batchUser" + i);
                batchParams[i].put("ip", "10.0.0." + i);
            }

            int[] results = dynamicJdbcTemplate.batchUpdate(DS_NAME, sql, batchParams);

            assertEquals(3, results.length);
            for (int i = 0; i < results.length; i++) {
                assertEquals(1, results[i], "第 " + i + " 条应影响 1 行");
            }

            List<Map<String, Object>> all = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID LIKE 'batchUser%' ORDER BY LOG_ID");
            assertEquals(3, all.size());
        }
    }

    // ==================== 原始 SQL 执行 ====================

    @Nested
    @DisplayName("execSql - 原始 SQL 执行")
    class ExecSqlTest {

        @Test
        @DisplayName("execSql SELECT 返回查询结果")
        void shouldExecSqlSelect() {
            insertLog("E001", "userK", "127.0.0.1");

            List<Map<String, Object>> result = dynamicJdbcTemplate.execSql(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE LOG_ID = 'E001'",
                    DynamicJdbcTemplate.OperationType.SELECT);

            assertEquals(1, result.size());
            assertEquals("userK", result.get(0).get("USER_ID"));
        }

        @Test
        @DisplayName("execSql INSERT 返回影响行数")
        void shouldExecSqlInsert() {
            List<Map<String, Object>> result = dynamicJdbcTemplate.execSql(DS_NAME,
                    "INSERT INTO " + TABLE + " (LOG_ID, USER_ID) VALUES ('E002', 'userL')",
                    DynamicJdbcTemplate.OperationType.INSERT);

            assertFalse(result.isEmpty());
            assertNotNull(result.get(0).get("rows"));
            assertEquals(1, ((Number) result.get(0).get("rows")).intValue());
        }

        @Test
        @DisplayName("execSql DELETE 返回影响行数")
        void shouldExecSqlDelete() {
            insertLog("E003", "userM", "127.0.0.1");

            List<Map<String, Object>> result = dynamicJdbcTemplate.execSql(DS_NAME,
                    "DELETE FROM " + TABLE + " WHERE LOG_ID = 'E003'",
                    DynamicJdbcTemplate.OperationType.DELETE);

            assertNotNull(result.get(0).get("rows"));
            assertEquals(1, ((Number) result.get(0).get("rows")).intValue());
        }
    }

    // ==================== 事务测试 ====================

    @Nested
    @DisplayName("事务测试 - TransactionTemplate + DataSourceTransactionManager")
    class TransactionalTest {

        @Test
        @DisplayName("事务正常提交 - 数据持久化")
        void shouldCommitWhenNoException() {
            transactionTemplate.execute(status -> {
                insertLog("T001", "transUser", "127.0.0.1");
                insertLog("T002", "transUser", "127.0.0.2");
                return null;
            });

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = 'transUser' ORDER BY LOG_ID");
            assertEquals(2, result.size(), "事务提交后应有 2 条记录");
        }

        @Test
        @DisplayName("事务异常回滚 - 数据不持久化")
        void shouldRollbackWhenException() {
            assertThrows(RuntimeException.class, () ->
                transactionTemplate.execute(status -> {
                    insertLog("T003", "rollbackUser", "127.0.0.1");
                    insertLog("T004", "rollbackUser", "127.0.0.2");
                    throw new RuntimeException("模拟业务异常，触发事务回滚");
                }));

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = 'rollbackUser'");
            assertTrue(result.isEmpty(), "事务回滚后不应有记录");
        }

        @Test
        @DisplayName("事务中第二条 SQL 失败 - 全部回滚")
        void shouldRollbackWhenSecondSqlFails() {
            assertThrows(RuntimeException.class, () ->
                transactionTemplate.execute(status -> {
                    insertLog("T005", "partialUser", "127.0.0.1");
                    // 第二条 SQL 故意违反 NOT NULL 约束（LOG_ID 为 NOT NULL）
                    Map<String, Object> params = new HashMap<>();
                    params.put("userId", "partialUser");
                    dynamicJdbcTemplate.update(DS_NAME,
                            "INSERT INTO " + TABLE + " (USER_ID) VALUES (:userId)", params);
                    return null;
                }));

            List<Map<String, Object>> result = dynamicJdbcTemplate.queryForList(DS_NAME,
                    "SELECT * FROM " + TABLE + " WHERE USER_ID = 'partialUser'");
            assertTrue(result.isEmpty(), "部分失败时事务应全部回滚，不应有记录");
        }
    }

    // ==================== 工具方法 ====================

    @Nested
    @DisplayName("静态工具方法")
    class StaticUtilTest {

        @Test
        @DisplayName("camelToUnderscore 驼峰转下划线")
        void shouldConvertCamelToUnderscore() {
            assertEquals("log_id", DynamicJdbcTemplate.camelToUnderscore("logId"));
            assertEquals("user_id", DynamicJdbcTemplate.camelToUnderscore("userId"));
            assertEquals("ip_address", DynamicJdbcTemplate.camelToUnderscore("ipAddress"));
            assertEquals("simple", DynamicJdbcTemplate.camelToUnderscore("simple"));
            assertEquals("", DynamicJdbcTemplate.camelToUnderscore(""));
            assertNull(DynamicJdbcTemplate.camelToUnderscore(null));
        }

        @Test
        @DisplayName("underscoreToCamel 下划线转驼峰")
        void shouldConvertUnderscoreToCamel() {
            assertEquals("logId", DynamicJdbcTemplate.underscoreToCamel("log_id"));
            assertEquals("userId", DynamicJdbcTemplate.underscoreToCamel("user_id"));
            assertEquals("ipAddress", DynamicJdbcTemplate.underscoreToCamel("ip_address"));
            assertEquals("simple", DynamicJdbcTemplate.underscoreToCamel("simple"));
            assertEquals("", DynamicJdbcTemplate.underscoreToCamel(""));
            assertNull(DynamicJdbcTemplate.underscoreToCamel(null));
        }

        @Test
        @DisplayName("互转一致性 - 驼峰转下划线再转回应与原值一致")
        void shouldRoundTripConversion() {
            String[] originals = {"logId", "userId", "ipAddress", "browserType"};
            for (String original : originals) {
                String underscore = DynamicJdbcTemplate.camelToUnderscore(original);
                String camel = DynamicJdbcTemplate.underscoreToCamel(underscore);
                assertEquals(original, camel, "互转不一致: " + original);
            }
        }
    }
}
