-- Rubric profile/version/dimension schema and initial ACTIVE V1 seed.

CREATE TABLE IF NOT EXISTS rubric_profiles (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    description TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rubric_versions (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES rubric_profiles(id) ON DELETE CASCADE,
    version VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    native_scale VARCHAR(50) NOT NULL,
    max_native_score DECIMAL(6,2) NOT NULL,
    prompt_instructions TEXT NOT NULL,
    result_schema_version VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rubric_dimensions (
    id BIGSERIAL PRIMARY KEY,
    rubric_version_id BIGINT NOT NULL REFERENCES rubric_versions(id) ON DELETE CASCADE,
    dimension_key VARCHAR(80) NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT,
    max_score DECIMAL(6,2) NOT NULL,
    weight DECIMAL(6,4) NOT NULL,
    sort_order INTEGER NOT NULL,
    level_descriptors_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_rubric_versions_profile_status ON rubric_versions(profile_id, status);
CREATE INDEX IF NOT EXISTS idx_rubric_dimensions_version_order ON rubric_dimensions(rubric_version_id, sort_order);

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$GENERAL$txt$, $txt$通用英语作文$txt$, $txt$GENERAL$txt$, $txt$适用于未指定考试场景的通用英语作文评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$GENERAL_V1$txt$, 'ACTIVE', $txt$PERCENT_100$txt$, 100, $txt$按通用英语写作质量评分，关注内容质量、结构组织、语言准确性和表达丰富度。不要使用 IELTS/TOEFL/CET 专属分档。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$content_quality$txt$, $txt$内容质量$txt$, $txt$主题是否明确，内容是否充分、合理、有说服力。$txt$, 30, 30, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization$txt$, $txt$结构组织$txt$, $txt$段落安排、开头结尾、逻辑顺序和整体组织是否清晰。$txt$, 25, 25, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_accuracy$txt$, $txt$语言准确性$txt$, $txt$语法、拼写、标点、搭配和句法是否准确。$txt$, 25, 25, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$expression$txt$, $txt$表达丰富度$txt$, $txt$词汇、句式和表达是否自然、多样、符合英语习惯。$txt$, 20, 20, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$JUNIOR_GENERAL$txt$, $txt$初中英语作文$txt$, $txt$JUNIOR$txt$, $txt$适用于初中阶段日常英语作文练习。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$JUNIOR_GENERAL_V1$txt$, 'ACTIVE', $txt$PERCENT_100$txt$, 100, $txt$按初中英语学习阶段评分，优先关注任务完成、基础语言准确性和清晰表达，不以成人考试标准苛责。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$task_completion$txt$, $txt$任务完成$txt$, $txt$是否回应题目要求，覆盖必要信息和写作目的。$txt$, 30, 30, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$basic_accuracy$txt$, $txt$基础语言准确性$txt$, $txt$基础语法、时态、人称、拼写和常用表达是否正确。$txt$, 30, 30, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization$txt$, $txt$结构清晰$txt$, $txt$句子和段落是否有基本顺序，表达是否容易理解。$txt$, 20, 20, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$vocabulary_expression$txt$, $txt$词汇与表达$txt$, $txt$词汇是否符合初中水平，表达是否自然且有适当变化。$txt$, 20, 20, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$JUNIOR_ZHONGKAO$txt$, $txt$中考英语作文$txt$, $txt$JUNIOR$txt$, $txt$适用于中考英语作文常见 20 分制评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$JUNIOR_ZHONGKAO_V1$txt$, 'ACTIVE', $txt$ZHONGKAO_20$txt$, 20, $txt$按中考英语作文常见 20 分制评分，重点检查要点覆盖、语言准确、结构连贯和格式规范。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$content_points$txt$, $txt$内容要点$txt$, $txt$题目要求的要点是否完整覆盖，内容是否切题。$txt$, 8, 8, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_accuracy$txt$, $txt$语言准确性$txt$, $txt$基础语法、词汇、拼写、时态和句式是否准确。$txt$, 6, 6, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization_coherence$txt$, $txt$结构与连贯$txt$, $txt$行文是否有开头、主体、结尾，句间衔接是否清楚。$txt$, 4, 4, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$format_neatness$txt$, $txt$格式与规范$txt$, $txt$书信、通知等格式、语域、标点和大小写是否规范。$txt$, 2, 2, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$SENIOR_GENERAL$txt$, $txt$高中英语作文$txt$, $txt$SENIOR$txt$, $txt$适用于高中阶段非高考专项英语作文。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$SENIOR_GENERAL_V1$txt$, 'ACTIVE', $txt$PERCENT_100$txt$, 100, $txt$按高中英语写作水平评分，关注任务回应、内容展开、语言质量和连贯结构。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$task_response$txt$, $txt$任务回应$txt$, $txt$是否准确回应题目、体裁、对象和写作目的。$txt$, 30, 30, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$argument_development$txt$, $txt$内容展开$txt$, $txt$观点或信息是否充分展开，有无具体例子和合理说明。$txt$, 25, 25, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_quality$txt$, $txt$语言质量$txt$, $txt$词汇、语法、句式、搭配和表达是否准确自然。$txt$, 25, 25, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$coherence$txt$, $txt$连贯与结构$txt$, $txt$段落结构、逻辑推进、衔接手段和整体流畅度。$txt$, 20, 20, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$SENIOR_GAOKAO$txt$, $txt$高考英语作文$txt$, $txt$SENIOR$txt$, $txt$适用于高考英语作文常见 25 分制评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$SENIOR_GAOKAO_V1$txt$, 'ACTIVE', $txt$GAOKAO_25$txt$, 25, $txt$按高考英语作文 25 分制评分，重点关注要点覆盖、语言运用、连贯结构和格式语域。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$content_coverage$txt$, $txt$内容覆盖$txt$, $txt$是否覆盖题目全部要点，内容是否切题且信息完整。$txt$, 10, 10, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_application$txt$, $txt$语言运用$txt$, $txt$词汇、语法、句式和表达是否准确、得体、有一定丰富性。$txt$, 8, 8, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$coherence_structure$txt$, $txt$连贯与结构$txt$, $txt$段落安排、逻辑推进、衔接和整体可读性。$txt$, 5, 5, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$format_register$txt$, $txt$格式与语域$txt$, $txt$应用文格式、称呼、结尾、语气和交际目的是否得体。$txt$, 2, 2, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$CET4$txt$, $txt$大学英语四级作文$txt$, $txt$COLLEGE$txt$, $txt$适用于大学英语四级作文 15 分制评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$CET4_V1$txt$, 'ACTIVE', $txt$CET_15$txt$, 15, $txt$按大学英语四级作文 15 分制评分，重点关注切题、基本论述结构、语言准确性和词汇句式。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$relevance_content$txt$, $txt$切题与内容$txt$, $txt$是否切题，观点是否明确，内容是否足够支撑主题。$txt$, 5, 5, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization$txt$, $txt$结构与逻辑$txt$, $txt$引入、展开、结论和段落逻辑是否清晰。$txt$, 4, 4, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_accuracy$txt$, $txt$语言准确性$txt$, $txt$语法、拼写、标点、搭配和句法错误对理解的影响。$txt$, 4, 4, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$vocabulary_variety$txt$, $txt$词汇与句式$txt$, $txt$词汇和句式是否有一定变化，是否避免过度简单重复。$txt$, 2, 2, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$CET6$txt$, $txt$大学英语六级作文$txt$, $txt$COLLEGE$txt$, $txt$适用于大学英语六级作文 15 分制评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$CET6_V1$txt$, 'ACTIVE', $txt$CET_15$txt$, 15, $txt$按大学英语六级作文 15 分制评分，比四级更强调思想深度、论证质量和成熟表达。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$relevance_depth$txt$, $txt$切题与思想深度$txt$, $txt$是否切题，观点是否有深度，分析是否充分。$txt$, 5, 5, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization_logic$txt$, $txt$结构与论证逻辑$txt$, $txt$论证层次、因果/对比/例证逻辑和段落组织是否清楚。$txt$, 4, 4, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_accuracy$txt$, $txt$语言准确性$txt$, $txt$语法、拼写、搭配和句法错误是否影响表达。$txt$, 3, 3, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$expression_sophistication$txt$, $txt$表达丰富度$txt$, $txt$词汇准确性、句式复杂度、表达成熟度和自然度。$txt$, 3, 3, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$IELTS_TASK_1$txt$, $txt$雅思 Task 1 图表作文$txt$, $txt$IELTS$txt$, $txt$适用于 IELTS Academic Writing Task 1 图表/流程/地图作文。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$IELTS_TASK_1_V1$txt$, 'ACTIVE', $txt$IELTS_BAND_0_9$txt$, 9, $txt$按 IELTS Task 1 四项标准评分。Task Achievement 需关注概述、关键特征选择、数据比较和信息准确性；不要按议论文标准评分。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$task_achievement$txt$, $txt$Task Achievement$txt$, $txt$是否完成图表/流程/地图描述任务，是否有 overview，是否准确选择并比较关键特征。$txt$, 9, 9, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$coherence_cohesion$txt$, $txt$Coherence and Cohesion$txt$, $txt$信息组织、段落安排、衔接和指代是否清晰自然。$txt$, 9, 9, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$lexical_resource$txt$, $txt$Lexical Resource$txt$, $txt$词汇范围、准确性、改写能力和搭配自然度。$txt$, 9, 9, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$grammar_range_accuracy$txt$, $txt$Grammatical Range and Accuracy$txt$, $txt$语法结构范围、复杂句使用和准确性。$txt$, 9, 9, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$IELTS_TASK_2$txt$, $txt$雅思 Task 2 议论文$txt$, $txt$IELTS$txt$, $txt$适用于 IELTS Writing Task 2 议论文。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$IELTS_TASK_2_V1$txt$, 'ACTIVE', $txt$IELTS_BAND_0_9$txt$, 9, $txt$按 IELTS Task 2 四项标准评分。Task Response 需关注是否回应题目所有部分、立场清晰、观点展开充分。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$task_response$txt$, $txt$Task Response$txt$, $txt$是否回应题目所有部分，立场是否清晰，观点是否充分展开。$txt$, 9, 9, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$coherence_cohesion$txt$, $txt$Coherence and Cohesion$txt$, $txt$段落组织、逻辑推进、衔接和指代是否自然。$txt$, 9, 9, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$lexical_resource$txt$, $txt$Lexical Resource$txt$, $txt$词汇范围、准确性、搭配和表达灵活性。$txt$, 9, 9, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$grammar_range_accuracy$txt$, $txt$Grammatical Range and Accuracy$txt$, $txt$句式范围、复杂结构和语法准确性。$txt$, 9, 9, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$TOEFL_INDEPENDENT$txt$, $txt$托福独立写作$txt$, $txt$TOEFL$txt$, $txt$适用于 TOEFL Independent Writing 0-5 原生分制评分。$txt$, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$TOEFL_INDEPENDENT_V1$txt$, 'ACTIVE', $txt$TOEFL_WRITING_0_5$txt$, 5, $txt$按 TOEFL 独立写作 0-5 原生分制评分，关注观点展开、组织、语言使用和任务完成。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$development$txt$, $txt$Development$txt$, $txt$观点是否充分展开，有无具体原因、细节和例子。$txt$, 5, 5, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization$txt$, $txt$Organization$txt$, $txt$整体结构、段落安排和衔接是否清晰。$txt$, 5, 5, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_use$txt$, $txt$Language Use$txt$, $txt$词汇、语法、句式和表达是否准确自然。$txt$, 5, 5, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$task_fulfillment$txt$, $txt$Task Fulfillment$txt$, $txt$是否完成题目要求，立场是否明确并持续回应题目。$txt$, 5, 5, 4, '[]');
END $$;

DO $$
DECLARE
    p_id BIGINT;
    v_id BIGINT;
BEGIN

INSERT INTO rubric_profiles (type_code, display_name, stage, description, is_enabled, updated_at)
VALUES ($txt$TOEFL_INTEGRATED$txt$, $txt$托福综合写作（暂缓开放）$txt$, $txt$TOEFL$txt$, $txt$托福综合写作需要阅读材料和听力材料，第一版暂缓开放。$txt$, FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    stage = EXCLUDED.stage,
    description = EXCLUDED.description,
    is_enabled = EXCLUDED.is_enabled,
    updated_at = CURRENT_TIMESTAMP
RETURNING id INTO p_id;

INSERT INTO rubric_versions (profile_id, version, status, native_scale, max_native_score, prompt_instructions, result_schema_version, published_at)
VALUES (p_id, $txt$TOEFL_INTEGRATED_V1$txt$, 'ACTIVE', $txt$TOEFL_WRITING_0_5$txt$, 5, $txt$该类型第一版仅 seed，不对用户开放。未来需结合阅读和听力材料评分。$txt$, 'RUBRIC_RESULT_V1', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status,
    native_scale = EXCLUDED.native_scale,
    max_native_score = EXCLUDED.max_native_score,
    prompt_instructions = EXCLUDED.prompt_instructions,
    result_schema_version = EXCLUDED.result_schema_version,
    published_at = EXCLUDED.published_at
RETURNING id INTO v_id;

DELETE FROM rubric_dimensions WHERE rubric_version_id = v_id;

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$source_integration$txt$, $txt$Source Integration$txt$, $txt$是否准确整合阅读和听力来源信息。$txt$, 5, 5, 1, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$lecture_reading_relation$txt$, $txt$Reading/Listening Relationship$txt$, $txt$是否准确说明听力与阅读的关系。$txt$, 5, 5, 2, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$organization$txt$, $txt$Organization$txt$, $txt$信息组织和段落结构是否清晰。$txt$, 5, 5, 3, '[]');

INSERT INTO rubric_dimensions (rubric_version_id, dimension_key, label, description, max_score, weight, sort_order, level_descriptors_json)
VALUES (v_id, $txt$language_use$txt$, $txt$Language Use$txt$, $txt$语言使用是否准确、清楚、自然。$txt$, 5, 5, 4, '[]');
END $$;
