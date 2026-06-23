package io.github.openground.common.dbcheck.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.utils.IdUtil;
import io.github.openground.common.dbcheck.dao.DbCheckLogDao;
import io.github.openground.common.dbcheck.model.DbCheckLogDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * DbCheck 操作日志服务实现
 *
 * @author ground-auth
 */
@Slf4j
@Service
public class DbCheckLogServiceImpl implements DbCheckLogService {

    @Autowired
    private DbCheckLogDao dbCheckLogDao;

    @Override
    public void save(DbCheckLogDO logDo) {
        Date now = new Date();
        logDo.setId(IdUtil.getUUID());
        logDo.setCreateTime(now);
        logDo.setUpdateTime(now);
        dbCheckLogDao.insert(logDo);
        log.debug("DbCheck 日志已保存: type={}, success={}", logDo.getOperationType(), logDo.getSuccess());
    }

    @Override
    public PageInfo<DbCheckLogDO> pageList(DbCheckLogDO query) {
        PageHelper.startPage(query.getPageNum(), query.getPageSize());
        return new PageInfo<>(dbCheckLogDao.selectList(query));
    }
}
