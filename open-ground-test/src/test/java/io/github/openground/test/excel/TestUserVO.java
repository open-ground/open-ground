package io.github.openground.test.excel;

import io.github.openground.common.excel.annotation.ExcelField;
import io.github.openground.common.excel.annotation.ExcelTemplate;
import io.github.openground.common.excel.enums.QueryType;

import java.util.Date;

/**
 * 测试用的 VO 类
 */
@ExcelTemplate(tableName = "sys_test_user", sheetName = "用户数据")
public class TestUserVO {

    @ExcelField(headerName = "用户名", order = 1, required = true)
    private String username;

    @ExcelField(headerName = "昵称", order = 2)
    private String nickname;

    @ExcelField(headerName = "性别", order = 3, dictType = "gender")
    private String gender;

    @ExcelField(headerName = "邮箱", order = 4, required = true)
    private String email;

    @ExcelField(headerName = "手机号", order = 5, dateFormat = "yyyy-MM-dd")
    private Date createTime;

    @ExcelField(headerName = "状态", order = 6, dictType = "status", exportIgnore = true)
    private Integer status;

    public TestUserVO() {}

    public TestUserVO(String username, String nickname, String gender, String email, Date createTime, Integer status) {
        this.username = username;
        this.nickname = nickname;
        this.gender = gender;
        this.email = email;
        this.createTime = createTime;
        this.status = status;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
