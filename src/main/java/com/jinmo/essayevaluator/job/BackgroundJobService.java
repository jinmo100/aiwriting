package com.jinmo.essayevaluator.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.mapper.BackgroundJobMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 轻量后台任务服务。
 *
 * <p>该服务只提供 RAG MVP 需要的最小闭环：创建/复用、领取锁定、终态流转和有限管理员查询。
 * payload/result 均经过敏感字段名检查，避免把明文密钥或系统 prompt 写入可查询任务表。</p>
 */
@Service
public class BackgroundJobService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_BUSINESS_KEY_LENGTH = 200;
    private static final int MAX_ERROR_CODE_LENGTH = 80;
    private static final int MAX_SAFE_MESSAGE_LENGTH = 1000;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private final BackgroundJobMapper backgroundJobMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public BackgroundJobService(BackgroundJobMapper backgroundJobMapper, ObjectMapper objectMapper) {
        this(backgroundJobMapper, objectMapper, Clock.systemDefaultZone());
    }

    BackgroundJobService(BackgroundJobMapper backgroundJobMapper, ObjectMapper objectMapper, Clock clock) {
        this.backgroundJobMapper = backgroundJobMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public BackgroundJob createOrReuse(
        BackgroundJobType type,
        Long ownerUserId,
        Long requestedByUserId,
        String businessKey,
        Object payload
    ) {
        validateCreateRequest(type, ownerUserId, businessKey);
        BackgroundJob active = backgroundJobMapper.findActive(type.value(), ownerUserId, businessKey);
        if (active != null) {
            return active;
        }

        LocalDateTime now = now();
        BackgroundJob job = new BackgroundJob();
        job.setJobType(type);
        job.setOwnerUserId(ownerUserId);
        job.setRequestedByUserId(requestedByUserId);
        job.setBusinessKey(businessKey);
        job.setPayloadJson(toSafeJson(payload));
        job.setStatus(BackgroundJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        job.setRunAfter(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        try {
            backgroundJobMapper.insert(job);
            return job;
        } catch (DataIntegrityViolationException duplicateActiveJob) {
            // 并发创建时以数据库部分唯一索引为最终兜底，再查一次活跃任务并复用。
            BackgroundJob racedActive = backgroundJobMapper.findActive(type.value(), ownerUserId, businessKey);
            if (racedActive != null) {
                return racedActive;
            }
            throw duplicateActiveJob;
        }
    }

    @Transactional
    public Optional<BackgroundJob> claimRunnableJob(Long id, String lockedBy, Duration lockTtl) {
        if (id == null) {
            throw new BusinessException("后台任务 ID 不能为空");
        }
        if (lockedBy == null || lockedBy.isBlank()) {
            throw new BusinessException("后台任务执行器标识不能为空");
        }
        Duration effectiveTtl = lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()
            ? Duration.ofMinutes(10)
            : lockTtl;
        LocalDateTime now = now();
        LocalDateTime lockedUntil = now.plus(effectiveTtl);
        int updated = backgroundJobMapper.claim(id, lockedBy, lockedUntil, now);
        if (updated == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(backgroundJobMapper.selectById(id));
    }

    @Transactional
    public BackgroundJob markCompleted(Long id, Object result) {
        BackgroundJob update = terminalUpdate(id, BackgroundJobStatus.COMPLETED);
        update.setResultJson(toSafeJson(result));
        update.setErrorCode(null);
        update.setErrorMessage(null);
        backgroundJobMapper.updateById(update);
        return update;
    }

    @Transactional
    public BackgroundJob markFailed(Long id, String errorCode, String safeMessage) {
        BackgroundJob existing = requireExisting(id);
        BackgroundJob update = terminalUpdate(id, BackgroundJobStatus.FAILED);
        update.setAttemptCount(Optional.ofNullable(existing.getAttemptCount()).orElse(0) + 1);
        update.setErrorCode(sanitizeErrorCode(errorCode));
        update.setErrorMessage(sanitizeSafeMessage(safeMessage));
        backgroundJobMapper.updateById(update);
        return update;
    }

    @Transactional
    public BackgroundJob markSkipped(Long id, Object result) {
        BackgroundJob update = terminalUpdate(id, BackgroundJobStatus.SKIPPED);
        update.setResultJson(toSafeJson(result));
        update.setErrorCode(null);
        update.setErrorMessage(null);
        backgroundJobMapper.updateById(update);
        return update;
    }

    @Transactional(readOnly = true)
    public List<BackgroundJob> listForAdmin(BackgroundJobType jobType, BackgroundJobStatus status) {
        QueryWrapper<BackgroundJob> query = new QueryWrapper<>();
        query.eq(jobType != null, "job_type", jobType == null ? null : jobType.value())
            .eq(status != null, "status", status == null ? null : status.value())
            .orderByDesc("created_at")
            .last("LIMIT 100");
        return backgroundJobMapper.selectList(query);
    }

    @Transactional(readOnly = true)
    public BackgroundJob findLatestForOwner(BackgroundJobType jobType, Long ownerUserId) {
        validateLatestQuery(jobType, ownerUserId);
        return backgroundJobMapper.findLatestForOwnerAndType(jobType.value(), ownerUserId);
    }

    @Transactional(readOnly = true)
    public BackgroundJob getById(Long id) {
        if (id == null) {
            return null;
        }
        return backgroundJobMapper.selectById(id);
    }

    private BackgroundJob terminalUpdate(Long id, BackgroundJobStatus status) {
        if (id == null) {
            throw new BusinessException("后台任务 ID 不能为空");
        }
        LocalDateTime now = now();
        BackgroundJob update = new BackgroundJob();
        update.setId(id);
        update.setStatus(status);
        update.setLockedBy(null);
        update.setLockedUntil(null);
        update.setFinishedAt(now);
        update.setUpdatedAt(now);
        return update;
    }

    private BackgroundJob requireExisting(Long id) {
        if (id == null) {
            throw new BusinessException("后台任务 ID 不能为空");
        }
        BackgroundJob job = backgroundJobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException("后台任务不存在");
        }
        return job;
    }

    private void validateCreateRequest(BackgroundJobType type, Long ownerUserId, String businessKey) {
        if (type == null) {
            throw new BusinessException("后台任务类型不能为空");
        }
        if (ownerUserId == null) {
            throw new BusinessException("后台任务归属用户不能为空");
        }
        if (businessKey == null || businessKey.isBlank()) {
            throw new BusinessException("后台任务业务键不能为空");
        }
        if (businessKey.length() > MAX_BUSINESS_KEY_LENGTH) {
            throw new BusinessException("后台任务业务键过长");
        }
    }

    private void validateLatestQuery(BackgroundJobType type, Long ownerUserId) {
        if (type == null) {
            throw new BusinessException("后台任务类型不能为空");
        }
        if (ownerUserId == null) {
            throw new BusinessException("后台任务归属用户不能为空");
        }
    }

    private String toSafeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode json = objectMapper.valueToTree(value);
            rejectSensitiveFields(json, "$");
            return objectMapper.writeValueAsString(json);
        } catch (IllegalArgumentException | JsonProcessingException error) {
            throw new BusinessException("后台任务内容序列化失败");
        }
    }

    private void rejectSensitiveFields(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveFieldName(field.getKey())) {
                    throw new BusinessException("后台任务内容包含敏感字段，已拒绝保存");
                }
                rejectSensitiveFields(field.getValue(), path + "." + field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                rejectSensitiveFields(child, path + "[]");
            }
        }
    }

    private boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalized.contains("apikey")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("authorization")
            || normalized.contains("accesstoken")
            || normalized.contains("refreshtoken")
            || normalized.equals("token")
            || normalized.contains("systemprompt");
    }

    private String sanitizeErrorCode(String errorCode) {
        String code = errorCode == null || errorCode.isBlank() ? "JOB_FAILED" : errorCode.trim();
        return code.length() > MAX_ERROR_CODE_LENGTH ? code.substring(0, MAX_ERROR_CODE_LENGTH) : code;
    }

    private String sanitizeSafeMessage(String safeMessage) {
        String message = safeMessage == null || safeMessage.isBlank()
            ? "后台任务执行失败，请稍后重试"
            : CONTROL_CHARS.matcher(safeMessage.trim()).replaceAll("");
        return message.length() > MAX_SAFE_MESSAGE_LENGTH ? message.substring(0, MAX_SAFE_MESSAGE_LENGTH) : message;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
