package io.github.openground.common.security.impl;

import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenStore;
import io.github.openground.common.security.mapper.SessionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于数据库的 Token 存储实现
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Component("dbTokenStore")
@ConditionalOnProperty(name = "ground.security.token-store", havingValue = "db", matchIfMissing = true)
public class DbTokenStore implements TokenStore {

    @Autowired
    private SessionMapper sessionMapper;

    @PostConstruct
    public void init() {
        log.info("DbTokenStore initialized - TokenStore type: DB, Bean name: dbTokenStore");
    }

    @Override
    public SessionEntity findByToken(String token) {
        return sessionMapper.findByToken(token);
    }

    @Override
    public SessionEntity findById(String id) {
        return sessionMapper.findById(id);
    }

    @Override
    public List<SessionEntity> findByUsername(String username) {
        return sessionMapper.findByUsername(username);
    }

    @Override
    public void save(SessionEntity session) {
        sessionMapper.save(session);
    }

    @Override
    public void update(SessionEntity session) {
        sessionMapper.update(session);
    }

    @Override
    public void deleteById(String id) {
        sessionMapper.deleteById(id);
    }

    @Override
    public void deleteByUsername(String username) {
        sessionMapper.deleteByUsername(username);
    }

    @Override
    public void cleanExpired() {
        sessionMapper.cleanExpired();
    }
}