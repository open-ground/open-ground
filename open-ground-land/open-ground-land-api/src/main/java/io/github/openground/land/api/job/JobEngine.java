package io.github.openground.land.api.job;


import io.github.openground.land.api.domain.JobOut;

import java.util.Map;

/**
 * <p>Title: JobEngine</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 * @Date 2020/7/22 13:33
 * @Version 3.0.0
 */
//@Service
//@Slf4j
//@Transactional(propagation= Propagation.REQUIRES_NEW)
public abstract class JobEngine {

    public abstract JobOut execute(Map<String, Object> param) throws Exception;

}
