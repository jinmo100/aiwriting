package com.jinmo.essayevaluator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.mapper.BackgroundJobMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundJobServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-09T02:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private BackgroundJobMapper backgroundJobMapper;

    @Test
    void createOrReuseReturnsExistingActiveJob() {
        BackgroundJob existing = new BackgroundJob();
        existing.setId(99L);
        existing.setJobType(BackgroundJobType.RAG_INDEX);
        existing.setOwnerUserId(7L);
        existing.setBusinessKey("kb:1");
        existing.setStatus(BackgroundJobStatus.PENDING);
        when(backgroundJobMapper.findActive("RAG_INDEX", 7L, "kb:1")).thenReturn(existing);

        BackgroundJob job = newService().createOrReuse(
            BackgroundJobType.RAG_INDEX,
            7L,
            7L,
            "kb:1",
            Map.of("embeddingConfigId", 1L)
        );

        assertThat(job).isSameAs(existing);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        verify(backgroundJobMapper, never()).insert(any(BackgroundJob.class));
    }

    @Test
    void createOrReuseAllowsNewJobAfterTerminalStatus() {
        when(backgroundJobMapper.findActive("RAG_INDEX", 7L, "kb:1")).thenReturn(null);
        doAnswer(invocation -> {
            BackgroundJob inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        }).when(backgroundJobMapper).insert(any(BackgroundJob.class));

        BackgroundJob job = newService().createOrReuse(
            BackgroundJobType.RAG_INDEX,
            7L,
            8L,
            "kb:1",
            Map.of("embeddingConfigId", 1L, "force", false)
        );

        assertThat(job.getId()).isEqualTo(101L);
        assertThat(job.getJobType()).isEqualTo(BackgroundJobType.RAG_INDEX);
        assertThat(job.getOwnerUserId()).isEqualTo(7L);
        assertThat(job.getRequestedByUserId()).isEqualTo(8L);
        assertThat(job.getBusinessKey()).isEqualTo("kb:1");
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(3);
        assertThat(job.getRunAfter()).isEqualTo(NOW);
        assertThat(job.getPayloadJson()).contains("embeddingConfigId").contains("force");
    }

    @Test
    void claimRunnableJobSetsRunningAndLock() {
        LocalDateTime lockedUntil = NOW.plusMinutes(5);
        BackgroundJob running = new BackgroundJob();
        running.setId(11L);
        running.setStatus(BackgroundJobStatus.RUNNING);
        running.setLockedBy("worker-1");
        running.setLockedUntil(lockedUntil);
        running.setStartedAt(NOW);
        when(backgroundJobMapper.claim(eq(11L), eq("worker-1"), any(LocalDateTime.class), eq(NOW))).thenReturn(1);
        when(backgroundJobMapper.selectById(11L)).thenReturn(running);

        Optional<BackgroundJob> claimed = newService().claimRunnableJob(11L, "worker-1", Duration.ofMinutes(5));

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(claimed.get().getLockedBy()).isEqualTo("worker-1");
        assertThat(claimed.get().getLockedUntil()).isEqualTo(lockedUntil);
        ArgumentCaptor<LocalDateTime> lockCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(backgroundJobMapper).claim(eq(11L), eq("worker-1"), lockCaptor.capture(), eq(NOW));
        assertThat(lockCaptor.getValue()).isEqualTo(lockedUntil);
    }

    @Test
    void claimSqlAllowsExpiredRunningJobRecovery() throws NoSuchMethodException {
        Update update = BackgroundJobMapper.class
            .getMethod("claim", Long.class, String.class, LocalDateTime.class, LocalDateTime.class)
            .getAnnotation(Update.class);
        String sql = String.join("\n", update.value());

        // RUNNING 锁过期后必须允许重新领取，否则进程崩溃会让 active business key 永久卡死。
        assertThat(sql)
            .contains("status = 'PENDING'")
            .contains("status = 'RUNNING'")
            .contains("locked_until <= #{now}");
    }

    @Test
    void markFailedStoresSafeErrorAndAttemptCount() {
        BackgroundJob existing = new BackgroundJob();
        existing.setId(12L);
        existing.setAttemptCount(2);
        existing.setLockedBy("worker-1");
        existing.setLockedUntil(NOW.plusMinutes(5));
        when(backgroundJobMapper.selectById(12L)).thenReturn(existing);

        BackgroundJob failed = newService().markFailed(
            12L,
            "EMBEDDING_PROVIDER_TIMEOUT",
            "Embedding 服务响应超时，请稍后重试"
        );

        ArgumentCaptor<BackgroundJob> updateCaptor = ArgumentCaptor.forClass(BackgroundJob.class);
        verify(backgroundJobMapper).updateById(updateCaptor.capture());
        BackgroundJob update = updateCaptor.getValue();
        assertThat(update.getId()).isEqualTo(12L);
        assertThat(update.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(update.getAttemptCount()).isEqualTo(3);
        assertThat(update.getErrorCode()).isEqualTo("EMBEDDING_PROVIDER_TIMEOUT");
        assertThat(update.getErrorMessage()).isEqualTo("Embedding 服务响应超时，请稍后重试");
        assertThat(update.getFinishedAt()).isEqualTo(NOW);
        assertThat(update.getLockedBy()).isNull();
        assertThat(update.getLockedUntil()).isNull();
        assertThat(failed).isSameAs(update);
    }

    @Test
    void markSkippedStoresActionableResult() {
        BackgroundJob skipped = newService().markSkipped(
            13L,
            Map.of("reason", "请先配置 Embedding", "action", "OPEN_EMBEDDING_CONFIG")
        );

        ArgumentCaptor<BackgroundJob> updateCaptor = ArgumentCaptor.forClass(BackgroundJob.class);
        verify(backgroundJobMapper).updateById(updateCaptor.capture());
        BackgroundJob update = updateCaptor.getValue();
        assertThat(update.getId()).isEqualTo(13L);
        assertThat(update.getStatus()).isEqualTo(BackgroundJobStatus.SKIPPED);
        assertThat(update.getResultJson()).contains("请先配置 Embedding").contains("OPEN_EMBEDDING_CONFIG");
        assertThat(update.getFinishedAt()).isEqualTo(NOW);
        assertThat(update.getErrorCode()).isNull();
        assertThat(update.getErrorMessage()).isNull();
        assertThat(skipped).isSameAs(update);
    }

    private BackgroundJobService newService() {
        return new BackgroundJobService(backgroundJobMapper, new ObjectMapper(), FIXED_CLOCK);
    }
}
