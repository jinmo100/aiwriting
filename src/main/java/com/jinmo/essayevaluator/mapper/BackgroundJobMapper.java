package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.job.BackgroundJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 后台任务 Mapper。
 *
 * <p>活跃任务查找和 claim 使用显式 SQL，确保与 V11 的部分唯一索引和锁字段语义保持一致。</p>
 */
@Mapper
public interface BackgroundJobMapper extends BaseMapper<BackgroundJob> {

    @Select("""
        SELECT *
        FROM background_jobs
        WHERE job_type = #{jobType}
          AND owner_user_id = #{ownerUserId}
          AND business_key = #{businessKey}
          AND status IN ('PENDING', 'RUNNING')
        ORDER BY created_at ASC
        LIMIT 1
        """)
    BackgroundJob findActive(
        @Param("jobType") String jobType,
        @Param("ownerUserId") Long ownerUserId,
        @Param("businessKey") String businessKey
    );

    @Update("""
        UPDATE background_jobs
        SET status = 'RUNNING',
            locked_by = #{lockedBy},
            locked_until = #{lockedUntil},
            started_at = COALESCE(started_at, #{now}),
            updated_at = #{now}
        WHERE id = #{id}
          AND run_after <= #{now}
          AND (
              status = 'PENDING'
              OR (status = 'RUNNING' AND locked_until <= #{now})
          )
        """)
    int claim(
        @Param("id") Long id,
        @Param("lockedBy") String lockedBy,
        @Param("lockedUntil") LocalDateTime lockedUntil,
        @Param("now") LocalDateTime now
    );
}
