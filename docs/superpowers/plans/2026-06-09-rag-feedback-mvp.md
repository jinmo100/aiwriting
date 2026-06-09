---
change: rag-feedback-mvp
design-doc: docs/superpowers/specs/2026-06-09-rag-feedback-mvp-design.md
base-ref: 1440eb65fd9f98b3d1291381a84a22fd189a29bb
---

# RAG Feedback MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade RAG Feedback MVP with user-managed Embedding configs, pgvector knowledge indexing, a lightweight `background_jobs` framework, RAG feedback generation, citations, and frontend display.

**Architecture:** Keep scoring deterministic and unchanged; add RAG as a post-scoring async layer. Use a generic `background_jobs` table plus `RAG_INDEX` and `RAG_FEEDBACK` handlers; store Embedding vectors per user/config/version using pgvector `vector(1536)`.

**Tech Stack:** Java 21, Spring Boot 3.4.2, MyBatis-Plus, Flyway, PostgreSQL pgvector, Redis session, Java `HttpClient`, Vue 3, TypeScript, Element Plus, Vite.

---

## Scope and sequencing

This plan deliberately implements the backend foundation first, then RAG business handlers, then frontend integration. Each task produces a coherent commit. Keep `docs/INTERVIEW_GUIDE_AI_BACKEND.md` untracked and out of every commit unless the user explicitly asks otherwise.

## File map

### Database and deployment

- Modify: `src/main/resources/db/migration/V11__rag_knowledge_base.sql`
- Modify: `docker-compose.release.yml`
- Modify: `docs/DEPLOYMENT.md` if present; otherwise add pgvector deployment notes to `README.md`

### Backend common job layer

- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJob.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobStatus.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobType.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobHandler.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobService.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobDispatcher.java`
- Create: `src/main/java/com/jinmo/essayevaluator/job/BackgroundJobController.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/BackgroundJobMapper.java`
- Create: `src/main/java/com/jinmo/essayevaluator/config/BackgroundJobAsyncConfig.java`
- Test: `src/test/java/com/jinmo/essayevaluator/job/BackgroundJobServiceTest.java`

### Embedding layer

- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingProviderType.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingConfig.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingConfigService.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingConfigController.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingClient.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/OpenAiCompatibleEmbeddingClient.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingRequestDtos.java`
- Create: `src/main/java/com/jinmo/essayevaluator/embedding/EmbeddingResponseDtos.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/EmbeddingConfigMapper.java`
- Test: `src/test/java/com/jinmo/essayevaluator/embedding/EmbeddingConfigServiceTest.java`
- Test: `src/test/java/com/jinmo/essayevaluator/embedding/OpenAiCompatibleEmbeddingClientTest.java`

### RAG backend

- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagDocument.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagChunk.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagChunkEmbedding.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedback.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackCitation.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagIndexJobHandler.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagIndexService.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagIndexController.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/AdminRagIndexController.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagQueryBuilder.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagRetrievalService.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackPrompt.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackValidator.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackJobHandler.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackService.java`
- Create: `src/main/java/com/jinmo/essayevaluator/rag/RagFeedbackController.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/RagDocumentMapper.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/RagChunkMapper.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/RagChunkEmbeddingMapper.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/RagFeedbackMapper.java`
- Create: `src/main/java/com/jinmo/essayevaluator/mapper/RagFeedbackCitationMapper.java`
- Test: `src/test/java/com/jinmo/essayevaluator/rag/RagQueryBuilderTest.java`
- Test: `src/test/java/com/jinmo/essayevaluator/rag/RagFeedbackValidatorTest.java`
- Test: `src/test/java/com/jinmo/essayevaluator/rag/RagIndexJobHandlerTest.java`

### Frontend

- Create: `frontend/src/api/embedding.ts`
- Create: `frontend/src/api/rag.ts`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/components/NavBar.vue`
- Create: `frontend/src/views/EmbeddingConfigView.vue`
- Modify: `frontend/src/views/ResultView.vue`

---

### Task 1: Database schema, pgvector seed, and deployment docs

**Files:**
- Modify: `src/main/resources/db/migration/V11__rag_knowledge_base.sql`
- Modify: `docker-compose.release.yml`
- Modify or create: `docs/DEPLOYMENT.md`
- Test command: `.\gradlew.bat test --tests "*RuntimeRegressionTest*"`

- [ ] **Step 1: Write the Flyway migration**

Create `src/main/resources/db/migration/V11__rag_knowledge_base.sql` with these sections in order:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS background_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(50) NOT NULL,
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    business_key VARCHAR(200) NOT NULL,
    payload_json TEXT,
    result_json TEXT,
    error_code VARCHAR(80),
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    run_after TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(120),
    locked_until TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_background_jobs_active_business
    ON background_jobs(job_type, owner_user_id, business_key)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_background_jobs_claim
    ON background_jobs(status, run_after, locked_until, created_at);
```

Then add `embedding_configs`, `rag_documents`, `rag_chunks`, `rag_chunk_embeddings vector(1536)`, `rag_feedbacks`, and `rag_feedback_citations` exactly as specified in `openspec/changes/rag-feedback-mvp/specs/*/spec.md`.

- [ ] **Step 2: Add Chinese table and column comments**

For every new table and every new column, add `COMMENT ON TABLE` and `COMMENT ON COLUMN`. Example style:

```sql
COMMENT ON TABLE background_jobs IS '轻量通用后台任务表，用于统一承载 RAG 索引和 RAG Feedback 生成任务';
COMMENT ON COLUMN background_jobs.business_key IS '同一任务类型和用户维度下的业务去重键，仅允许一个待执行或执行中任务';
```

- [ ] **Step 3: Seed 30 knowledge cards**

Insert active `rag_documents` and `rag_chunks` for the 30 cards listed in `docs/AI_BACKEND_RAG_UPGRADE_PLAN.md`: 15 grammar, 10 writing, 5 exam strategy. Use `metadata_json` with stable fields:

```json
{"skillTag":"subject_verb_agreement","levelTag":"GENERAL","documentType":"GRAMMAR_KNOWLEDGE"}
```

Do not insert embedding vectors in Flyway.

- [ ] **Step 4: Update deployment**

In `docker-compose.release.yml`, replace `postgres:16-alpine` with `pgvector/pgvector:pg16`. In `docs/DEPLOYMENT.md`, add Windows/VPS notes: Docker release already includes pgvector; host PostgreSQL requires superuser-created `vector` extension before app migration.

- [ ] **Step 5: Validate and commit**

Run:

```powershell
.\gradlew.bat test --tests "*RuntimeRegressionTest*"
```

Expected: migration-related regression tests pass or no matching runtime tests fail. Commit:

```powershell
git add src/main/resources/db/migration/V11__rag_knowledge_base.sql docker-compose.release.yml docs/DEPLOYMENT.md openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(db): add rag knowledge schema"
```

---

### Task 2: Lightweight `background_jobs` framework

**Files:**
- Create/modify backend job files listed in the File map
- Modify: `src/main/java/com/jinmo/essayevaluator/service/CurrentUserService.java`
- Test: `src/test/java/com/jinmo/essayevaluator/job/BackgroundJobServiceTest.java`

- [ ] **Step 1: Write service tests first**

Create `BackgroundJobServiceTest` with tests named:

```java
createOrReuseReturnsExistingActiveJob()
createOrReuseAllowsNewJobAfterTerminalStatus()
claimRunnableJobSetsRunningAndLock()
markFailedStoresSafeErrorAndAttemptCount()
markSkippedStoresActionableResult()
```

Use Mockito for `BackgroundJobMapper`. Assert job type, owner user id, business key, status, lock fields, attempt count, and safe error message.

- [ ] **Step 2: Implement entity, enums, and mapper**

Create `BackgroundJob` with Lombok `@Data`, MyBatis-Plus `@TableName("background_jobs")`, `@TableId(type = IdType.AUTO)`, and fields matching the migration. Create enums:

```java
public enum BackgroundJobType { RAG_INDEX, RAG_FEEDBACK }
public enum BackgroundJobStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }
```

Create `BackgroundJobMapper extends BaseMapper<BackgroundJob>` with methods:

```java
BackgroundJob findActive(@Param("jobType") String jobType, @Param("ownerUserId") Long ownerUserId, @Param("businessKey") String businessKey);
int claim(@Param("id") Long id, @Param("lockedBy") String lockedBy, @Param("lockedUntil") LocalDateTime lockedUntil, @Param("now") LocalDateTime now);
```

- [ ] **Step 3: Implement service and dispatcher**

`BackgroundJobService` must expose:

```java
BackgroundJob createOrReuse(BackgroundJobType type, Long ownerUserId, Long requestedByUserId, String businessKey, Object payload);
BackgroundJob markCompleted(Long id, Object result);
BackgroundJob markFailed(Long id, String errorCode, String safeMessage);
BackgroundJob markSkipped(Long id, Object result);
```

`BackgroundJobDispatcher` receives a job id, claims the job, resolves a `BackgroundJobHandler` by type, executes it, and writes terminal status.

- [ ] **Step 4: Add admin job query and admin guard**

Add `CurrentUserService.requireAdmin()`:

```java
public void requireAdmin() {
    if (!isAdmin()) {
        throw new BusinessException("需要管理员权限");
    }
}
```

Add `GET /api/admin/jobs` returning only id, jobType, ownerUserId, requestedByUserId, status, safe result summary, safe error, createdAt, updatedAt.

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --tests "*BackgroundJobServiceTest*" --tests "*CurrentUserServiceTest*"
```

Expected: all targeted tests pass. Commit:

```powershell
git add src/main/java/com/jinmo/essayevaluator/job src/main/java/com/jinmo/essayevaluator/mapper/BackgroundJobMapper.java src/main/java/com/jinmo/essayevaluator/config/BackgroundJobAsyncConfig.java src/main/java/com/jinmo/essayevaluator/service/CurrentUserService.java src/test/java/com/jinmo/essayevaluator/job/BackgroundJobServiceTest.java src/test/java/com/jinmo/essayevaluator/service/CurrentUserServiceTest.java openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(job): add background job framework"
```

---

### Task 3: Embedding configuration and OpenAI-compatible client

**Files:**
- Create embedding package and mapper files listed in the File map
- Test: `EmbeddingConfigServiceTest`, `OpenAiCompatibleEmbeddingClientTest`

- [ ] **Step 1: Write tests for config ownership and 1536 validation**

Create service tests for:

```java
createConfigEncryptsApiKeyAndReturnsPreview()
setDefaultOnlyAffectsCurrentUser()
updateWithoutApiKeyKeepsExistingEncryptedKey()
rejectsDimensionsOtherThan1536()
loadOwnedConfigRejectsAnotherUser()
```

- [ ] **Step 2: Implement DTOs and service**

Use records for request/response:

```java
public record EmbeddingConfigCreateRequest(String configName, String providerType, String baseUrl, String apiKey, String modelName, Integer dimensions, Integer timeoutSeconds, Boolean isDefault) {}
public record EmbeddingConfigResponse(Long id, String configName, String providerType, String baseUrl, String modelName, Integer dimensions, Boolean isDefault, Boolean hasApiKey, String apiKeyPreview, String lastTestStatus, String lastTestMessage, Integer lastTestLatencyMs) {}
```

Service rules: current user only, dimensions exactly 1536, encrypt with `ApiKeyEncryptionService`, response never returns plaintext.

- [ ] **Step 3: Implement OpenAI-compatible embedding client**

Use Java 21 `java.net.http.HttpClient`. Request:

```json
{"model":"text-embedding-3-large","input":["test"],"dimensions":1536}
```

Response parser extracts `data[0].embedding` into `List<Double>` and checks size equals 1536.

- [ ] **Step 4: Add controller endpoints**

Expose:

```text
POST   /api/embedding-configs
GET    /api/embedding-configs
GET    /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}
DELETE /api/embedding-configs/{id}
PUT    /api/embedding-configs/{id}/default
POST   /api/embedding-configs/test
POST   /api/embedding-configs/{id}/test
```

Apply `RateLimitService` to test endpoints using key `embedding:test:user:<userId>`.

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --tests "*EmbeddingConfigServiceTest*" --tests "*OpenAiCompatibleEmbeddingClientTest*"
```

Expected: targeted tests pass. Commit:

```powershell
git add src/main/java/com/jinmo/essayevaluator/embedding src/main/java/com/jinmo/essayevaluator/mapper/EmbeddingConfigMapper.java src/test/java/com/jinmo/essayevaluator/embedding openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(embedding): add user embedding configs"
```

---

### Task 4: RAG index handler and status APIs

**Files:**
- Create RAG entity/mapper/index files listed in the File map
- Test: `src/test/java/com/jinmo/essayevaluator/rag/RagIndexJobHandlerTest.java`

- [ ] **Step 1: Write handler tests**

Test cases:

```java
indexHandlerWritesEmbeddingsForOwnerAndConfig()
indexHandlerMarksSkippedWhenConfigMissing()
indexHandlerDoesNotReadAnotherUsersConfig()
forceRebuildReplacesCurrentVersionEmbeddings()
```

Mock `EmbeddingClient`, `EmbeddingConfigService`, and RAG mappers. Use a deterministic 1536-length vector built by `Collections.nCopies(1536, 0.01d)`.

- [ ] **Step 2: Implement mapper SQL for vector writes and search preparation**

`RagChunkEmbeddingMapper` must include a method that writes vector literals safely through parameters:

```java
@Insert("""
    INSERT INTO rag_chunk_embeddings
      (user_id, embedding_config_id, chunk_id, embedding_model, embedding_dimension, embedding_version, content_hash, embedding_vector)
    VALUES
      (#{userId}, #{embeddingConfigId}, #{chunkId}, #{embeddingModel}, #{embeddingDimension}, #{embeddingVersion}, #{contentHash}, CAST(#{embeddingVectorLiteral} AS vector))
    ON CONFLICT (user_id, embedding_config_id, chunk_id, embedding_version)
    DO UPDATE SET content_hash = EXCLUDED.content_hash,
                  embedding_vector = EXCLUDED.embedding_vector,
                  updated_at = CURRENT_TIMESTAMP,
                  indexed_at = CURRENT_TIMESTAMP
    """)
int upsertEmbedding(...);
```

Convert `List<Double>` to pgvector literal format: `[0.01,0.02]`.

- [ ] **Step 3: Implement `RagIndexJobHandler`**

Handler reads payload, validates owner config, loads active chunks, embeds chunk content in batches, writes embeddings, and returns:

```json
{"totalChunks":30,"processedChunks":30,"failedChunks":0,"embeddingVersion":"text-embedding-3-large:1536:RAG_KB_V1"}
```

- [ ] **Step 4: Add user and admin index APIs**

User APIs:

```text
GET  /api/rag/index/my-status
POST /api/rag/index/rebuild-my
```

Admin APIs:

```text
GET  /api/admin/rag/index/status
POST /api/admin/rag/index/rebuild
```

Admin trigger sets `requestedByUserId` to the admin id and `ownerUserId` to the target user id.

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --tests "*RagIndexJobHandlerTest*" --tests "*BackgroundJobServiceTest*"
```

Expected: targeted tests pass. Commit:

```powershell
git add src/main/java/com/jinmo/essayevaluator/rag src/main/java/com/jinmo/essayevaluator/mapper/Rag*Mapper.java src/test/java/com/jinmo/essayevaluator/rag/RagIndexJobHandlerTest.java openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(rag): add knowledge index jobs"
```

---

### Task 5: RAG retrieval and feedback generation

**Files:**
- Create feedback/retrieval/prompt/validator files listed in the File map
- Test: `RagQueryBuilderTest`, `RagFeedbackValidatorTest`

- [ ] **Step 1: Write query builder tests**

Create `RagQueryBuilderTest` with cases:

```java
includesAtMostThreeLowScoreDimensions()
includesAtMostFiveAnnotations()
includesAtMostThreePriorityImprovements()
includesTaskPromptSummaryForSpecificEssayType()
```

Use `RubricScoringResult` test objects and assert the query contains dimension labels, annotation messages, and safe task prompt summary.

- [ ] **Step 2: Write feedback validator tests**

Create tests:

```java
acceptsValidFeedbackWithCitations()
rejectsMissingOverall()
rejectsMoreThanFiveItems()
rejectsItemWithoutCitation()
rejectsEmptyNextPractice()
```

- [ ] **Step 3: Implement retrieval**

`RagRetrievalService` must query only:

```text
rag_chunk_embeddings.user_id = current user
rag_chunk_embeddings.embedding_config_id = selected config
rag_chunks.is_active = true
rag_documents.is_active = true
rag_documents.essay_type is null or equals current essayType
```

Use cosine distance with pgvector:

```sql
ORDER BY embedding_vector <=> CAST(#{queryVectorLiteral} AS vector)
LIMIT #{topK}
```

- [ ] **Step 4: Implement prompt and validator**

`RagFeedbackPrompt` must label all essay, taskPrompt, scoring JSON, and citations as untrusted content. Required JSON schema:

```json
{
  "overall": "string",
  "items": [
    {
      "title": "string",
      "problem": "string",
      "whyItMatters": "string",
      "howToImprove": "string",
      "example": {"before": "string", "after": "string"},
      "citationIds": [1]
    }
  ],
  "nextPractice": ["string"]
}
```

- [ ] **Step 5: Implement `RagFeedbackJobHandler` and controller**

Handler validates score ownership and `COMPLETED` status, builds query, retrieves chunks, calls existing Chat Provider via current provider adapter path, validates JSON, saves `rag_feedbacks` and `rag_feedback_citations`.

Controller exposes:

```text
GET  /api/rag/feedbacks/{essayId}
POST /api/rag/feedbacks/{essayId}/generate
POST /api/rag/feedbacks/{essayId}/retry
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --tests "*RagQueryBuilderTest*" --tests "*RagFeedbackValidatorTest*"
```

Expected: targeted tests pass. Commit:

```powershell
git add src/main/java/com/jinmo/essayevaluator/rag src/test/java/com/jinmo/essayevaluator/rag openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(rag): add feedback generation"
```

---

### Task 6: Frontend Embedding config and RAG feedback UI

**Files:**
- Create/modify frontend files listed in the File map

- [ ] **Step 1: Add TypeScript types and APIs**

In `frontend/src/types/index.ts`, add `EmbeddingConfig`, `EmbeddingConfigRequest`, `EmbeddingTestResponse`, `RagIndexStatus`, `RagFeedback`, `RagFeedbackCitation`, `BackgroundJobStatus` types.

Create `frontend/src/api/embedding.ts` with functions matching backend endpoints. Create `frontend/src/api/rag.ts` with index and feedback functions.

- [ ] **Step 2: Add Embedding configuration page**

Create `EmbeddingConfigView.vue` using Element Plus card/table/dialog patterns from `ConfigView.vue`. Page must support create, edit, delete, set default, test unsaved config, test saved config, and show `hasApiKey`/preview only.

- [ ] **Step 3: Add route and navigation**

Add route:

```ts
{
  path: '/embedding-config',
  name: 'EmbeddingConfig',
  component: () => import('@/views/EmbeddingConfigView.vue'),
  meta: { title: 'Embedding 配置' }
}
```

Add navigation label `Embedding 配置` in `NavBar.vue`.

- [ ] **Step 4: Add index status block**

In `EmbeddingConfigView.vue`, show status text for `PENDING/RUNNING/COMPLETED/FAILED/SKIPPED`, progress `processedChunks / totalChunks`, failed count, last safe error, and a `构建我的知识索引` button that disables while active job exists.

- [ ] **Step 5: Add ResultView RAG feedback block**

In `ResultView.vue`, add section title `知识点增强反馈`. Handle states:

```text
未配置 Embedding -> 去配置
未构建索引 -> 构建我的知识索引
PENDING/RUNNING -> 知识点分析中
COMPLETED -> 展示 overall/items/nextPractice/citations
FAILED -> 提示不影响评分结果并显示重试按钮
```

Only render citation fields `sourceTitle`, `sourceType`, `snippet`, `rankNo`, `reason`.

- [ ] **Step 6: Build and commit**

Run:

```powershell
cd frontend
npm run build
cd ..
```

Expected: build passes. Commit:

```powershell
git add frontend/src/api/embedding.ts frontend/src/api/rag.ts frontend/src/types/index.ts frontend/src/router/index.ts frontend/src/components/NavBar.vue frontend/src/views/EmbeddingConfigView.vue frontend/src/views/ResultView.vue openspec/changes/rag-feedback-mvp/tasks.md
git commit -m "feat(frontend): add rag feedback UI"
```

---

### Task 7: End-to-end verification and OpenSpec alignment

**Files:**
- Modify: `openspec/changes/rag-feedback-mvp/tasks.md`
- Modify: documentation touched by implementation

- [ ] **Step 1: Run backend tests**

Run:

```powershell
.\gradlew.bat test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd frontend
npm run build
cd ..
```

Expected: Vite build passes; known chunk size warnings are acceptable if no errors occur.

- [ ] **Step 3: Run OpenSpec validation**

Run:

```powershell
openspec validate rag-feedback-mvp --strict
```

Expected: `Change 'rag-feedback-mvp' is valid`.

- [ ] **Step 4: Smoke with real dev services**

Using `.env.dev.local`, verify:

```text
login
create Chat Provider
create Embedding config with dimensions 1536
test Embedding config
build my knowledge index
submit essay
wait for scoring COMPLETED
generate RAG Feedback
view citations on result page
```

Record concise observations in the final implementation summary. Do not commit `.env.dev.local`.

- [ ] **Step 5: Final commit if verification changes files**

If task checkboxes or docs changed during verification, commit:

```powershell
git add openspec/changes/rag-feedback-mvp/tasks.md docs
git commit -m "docs: finalize rag feedback mvp verification"
```

## Self-review

- Spec coverage: `background-job-processing` is covered by Task 2; `embedding-config-management` by Task 3; `rag-knowledge-indexing` by Tasks 1 and 4; `rag-feedback-generation` by Task 5; `rag-feedback-frontend` by Task 6.
- Placeholder scan: no placeholder markers remain in this plan.
- Type consistency: job type names are `RAG_INDEX` and `RAG_FEEDBACK`; job statuses are `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`; Embedding dimensions are fixed at `1536`.
