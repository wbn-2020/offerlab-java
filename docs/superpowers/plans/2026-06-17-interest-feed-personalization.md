# Interest Feed Personalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first generalized-community release slice: users can express interest channels, the recommendation feed uses those interests plus existing community signals, and the UI explains/records recommendation feedback.

**Architecture:** Keep this release focused on an explainable rules-based recommendation loop rather than ML. Extend the existing `UserIntentDTO` as the user-owned preference source, reuse current topic/tag/feed/feedback APIs, and avoid introducing media upload, collections, or creator analytics in this round.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL JSON field via existing `t_user_profile.intent_json`, Redis-backed feed feedback, Vue 3, TypeScript, Vite, existing guard tests.

---

## Release Slices

### This Round: P0/P1 Closed Loop

This round implements only **interest channels and explainable recommendation feed**.

**P0**
- Add generalized interest fields to user intent without breaking existing job-search fields.
- Make the backend recommendation score prefer posts whose tags/topics/content match the user's selected interests.
- Keep existing hidden-post feedback behavior and ensure recommendation reasons mention interest matches.
- Add focused backend guard tests for DTO compatibility and scoring/reason behavior.

**P1**
- Update settings interest form to let users pick general community interests, not only job-search intent.
- Update home recommendation copy/reasons so the product no longer reads as only technical interview prep.
- Add focused frontend guard tests for interest fields and recommendation UI copy.

### Later Extensions

Not in this implementation round:
- 图文/媒体型帖子: image gallery, object storage, thumbnails, upload moderation.
- 收藏夹/专辑: custom favorite folders, public/private collections, collection pages.
- 创作者中心: creator dashboard, scheduled stats aggregation, weekly report.
- 统一审核队列升级: route all moderation/report sources into one operator workflow.
- ML/vector recommendation: embeddings, Qdrant/ES vector ranking, offline model pipeline.

---

## File Structure

### Backend Files

- Modify: `C:/project/offerlab-java/community-domain-user/src/main/java/com/offerlab/community/user/api/dto/UserIntentDTO.java`
  - Add `interestTopics`, `interestTags`, and `contentPreferences`.
  - Preserve `targetCompanies`, `targetPositions`, `targetCity`, `expectedCity`, `yearsOfExp`, and `techStack`.

- Modify: `C:/project/offerlab-java/community-domain-feed/src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java`
  - Include user interests in recommendation scoring and `recommendationReasons`.
  - Keep Redis hidden feedback filtering intact.

- Modify: `C:/project/offerlab-java/community-archtest/src/test/java/com/offerlab/community/archtest/UserIntentDTOJsonTest.java`
  - Add JSON compatibility assertions for new fields and existing aliases.

- Modify: `C:/project/offerlab-java/community-bootstrap/src/test/java/com/offerlab/community/feed/application/FeedFacadeVisibilityTest.java`
  - Add or extend the recommendation test so interest-matched content ranks higher and reasons are explainable.

### Frontend Files

- Modify: `C:/project/offerlab-vue/src/api/types.ts`
  - Add matching `UserIntent` fields.

- Modify: `C:/project/offerlab-vue/src/api/user.ts`
  - Send and adapt new interest fields while preserving old intent payload shape.

- Modify: `C:/project/offerlab-vue/src/components/user/IntentForm.vue`
  - Add general community interest controls.
  - Keep existing job-search fields available as optional profile context.

- Modify: `C:/project/offerlab-vue/src/views/HomeView.vue`
  - Update recommendation description to interest/community positioning.
  - Keep feed tabs and existing feedback menu behavior.

- Create: `C:/project/offerlab-vue/scripts/test-interest-feed-positioning.mjs`
  - Guard source text and API fields for the new P0/P1 slice.

- Modify: `C:/project/offerlab-vue/package.json`
  - Add `test:interest-feed-positioning` to `test:guards`.

---

## Task Decomposition And Parallel Boundaries

### Parallelization Decision

Use **two implementation subagents at most**, because backend and frontend files do not overlap and can be independently verified.

Do not create more subagents:
- Backend scoring and DTO compatibility touch shared Java types and tests, so they should stay in one backend subtask.
- Frontend API/types/form/home/test changes share `UserIntent` shape, so they should stay in one frontend subtask.
- Main agent performs final integration review and cross-repo verification.

### Subtask A: Backend Interest-Aware Recommendation

**Independent Goal:** Add generalized interest fields and make recommendation ranking/reasons use them.

**Responsible File Scope:**
- `C:/project/offerlab-java/community-domain-user/src/main/java/com/offerlab/community/user/api/dto/UserIntentDTO.java`
- `C:/project/offerlab-java/community-domain-feed/src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java`
- `C:/project/offerlab-java/community-archtest/src/test/java/com/offerlab/community/archtest/UserIntentDTOJsonTest.java`
- `C:/project/offerlab-java/community-bootstrap/src/test/java/com/offerlab/community/feed/application/FeedFacadeVisibilityTest.java`

**Must Not Modify:**
- Frontend files.
- Database migration/init SQL.
- Ops, search, notification, moderation, or question modules.

**Acceptance Criteria:**
- Existing user intent JSON with `targetCity` still maps to `expectedCity`.
- New JSON fields `interestTopics`, `interestTags`, and `contentPreferences` round-trip through Jackson.
- Recommendation score gives a visible boost to posts matching user interests through title, tags, topic-like labels, or content type where available.
- Recommendation reasons include at least one interest-driven reason when a match exists.
- Feedback-hidden posts remain filtered from recommend feed.

**Verification Commands:**
```powershell
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
mvn -pl community-bootstrap -Dtest=FeedFacadeVisibilityTest test
```

### Subtask B: Frontend Interest Settings And Recommendation Copy

**Independent Goal:** Let users save general interests and align home recommendation UI with broad community positioning.

**Responsible File Scope:**
- `C:/project/offerlab-vue/src/api/types.ts`
- `C:/project/offerlab-vue/src/api/user.ts`
- `C:/project/offerlab-vue/src/components/user/IntentForm.vue`
- `C:/project/offerlab-vue/src/views/HomeView.vue`
- `C:/project/offerlab-vue/scripts/test-interest-feed-positioning.mjs`
- `C:/project/offerlab-vue/package.json`

**Must Not Modify:**
- Backend files.
- Router, visual snapshots, global CSS, admin pages, search pages, or topic detail pages.

**Acceptance Criteria:**
- `UserIntent` includes `interestTopics`, `interestTags`, and `contentPreferences`.
- `userApi.updateIntent` preserves existing fields and sends new interest arrays.
- Settings interest form exposes generalized interests without removing existing job-search fields.
- Home recommendation description mentions interest/community matching rather than only engineering/job-search scenarios.
- A guard script verifies the new fields and key UI copy.

**Verification Commands:**
```powershell
npm run test:interest-feed-positioning
npm run typecheck
```

### Main Agent Integration

**Goal:** Review both subtasks, resolve integration mismatches, and run focused checks.

**Responsible File Scope:**
- Review all files touched by Subtask A and Subtask B.
- Only modify files from those scopes if integration fixes are needed.

**Acceptance Criteria:**
- Backend and frontend field names match exactly.
- No unrelated files were changed.
- Tests/build checks pass or failures are reported with exact failing command.

**Verification Commands:**
```powershell
cd C:/project/offerlab-java
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
mvn -pl community-bootstrap -Dtest=FeedFacadeVisibilityTest test

cd C:/project/offerlab-vue
npm run test:interest-feed-positioning
npm run typecheck
```

---

## Task 1: Backend Interest-Aware Recommendation

**Files:**
- Modify: `C:/project/offerlab-java/community-domain-user/src/main/java/com/offerlab/community/user/api/dto/UserIntentDTO.java`
- Modify: `C:/project/offerlab-java/community-domain-feed/src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java`
- Modify: `C:/project/offerlab-java/community-archtest/src/test/java/com/offerlab/community/archtest/UserIntentDTOJsonTest.java`
- Modify: `C:/project/offerlab-java/community-bootstrap/src/test/java/com/offerlab/community/feed/application/FeedFacadeVisibilityTest.java`

- [ ] **Step 1: Write DTO compatibility test**

Add a test method to `UserIntentDTOJsonTest`:

```java
@Test
void should_round_trip_general_community_interests_without_breaking_target_city_alias() throws Exception {
    UserIntentDTO dto = objectMapper.readValue("""
            {
              "targetCompanies": ["OpenAI"],
              "targetPositions": ["Backend"],
              "targetCity": "Shanghai",
              "techStack": ["Java"],
              "interestTopics": ["职场成长", "租房生活"],
              "interestTags": ["效率工具", "城市生活"],
              "contentPreferences": ["图文笔记", "经验复盘"]
            }
            """, UserIntentDTO.class);

    assertEquals("Shanghai", dto.getExpectedCity());
    assertEquals(List.of("职场成长", "租房生活"), dto.getInterestTopics());
    assertEquals(List.of("效率工具", "城市生活"), dto.getInterestTags());
    assertEquals(List.of("图文笔记", "经验复盘"), dto.getContentPreferences());

    String json = objectMapper.writeValueAsString(dto);
    assertTrue(json.contains("\"targetCity\":\"Shanghai\""));
    assertTrue(json.contains("\"interestTopics\""));
    assertTrue(json.contains("\"interestTags\""));
    assertTrue(json.contains("\"contentPreferences\""));
}
```

- [ ] **Step 2: Run DTO test and verify it fails before implementation**

Run:

```powershell
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
```

Expected: FAIL because `UserIntentDTO` does not yet expose all new fields.

- [ ] **Step 3: Add new fields to `UserIntentDTO`**

Add these fields to `UserIntentDTO`:

```java
private List<String> interestTopics;
private List<String> interestTags;
private List<String> contentPreferences;
```

Keep Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor`; do not remove the existing `targetCity` alias methods.

- [ ] **Step 4: Run DTO test and verify it passes**

Run:

```powershell
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
```

Expected: PASS.

- [ ] **Step 5: Write recommendation behavior test**

In `FeedFacadeVisibilityTest`, add a focused test that stubs a `UserIntentDTO` containing `interestTopics` or `interestTags`, two candidate posts, and verifies the matched post appears first and has a recommendation reason containing `兴趣` or `关注`.

The test should keep using existing mocks for `FeedInboxRedis`, `FeedFeedbackStore`, `PostFacade`, `UserFacade`, and `InteractionFacade`; do not introduce Spring context boot.

- [ ] **Step 6: Run recommendation test and verify it fails before implementation**

Run:

```powershell
mvn -pl community-bootstrap -Dtest=FeedFacadeVisibilityTest test
```

Expected: FAIL because current scoring/reasons do not account for new interest fields.

- [ ] **Step 7: Implement minimal scoring and reason changes**

In `FeedFacadeImpl`:

- Add helper methods that normalize interest strings from `interestTopics`, `interestTags`, `contentPreferences`, and existing `techStack`.
- Add score boosts when a post title, tags, content type label, or existing recommendation metadata contains a normalized interest.
- Add a reason such as `匹配你的兴趣：{interest}` for the first matched interest.
- Keep existing hot/latest/community-signal score logic.
- Keep `feedbackStore.hiddenPostIds(uid)` filtering unchanged.

- [ ] **Step 8: Run backend focused tests**

Run:

```powershell
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
mvn -pl community-bootstrap -Dtest=FeedFacadeVisibilityTest test
```

Expected: PASS.

---

## Task 2: Frontend Interest Settings And Recommendation Copy

**Files:**
- Modify: `C:/project/offerlab-vue/src/api/types.ts`
- Modify: `C:/project/offerlab-vue/src/api/user.ts`
- Modify: `C:/project/offerlab-vue/src/components/user/IntentForm.vue`
- Modify: `C:/project/offerlab-vue/src/views/HomeView.vue`
- Create: `C:/project/offerlab-vue/scripts/test-interest-feed-positioning.mjs`
- Modify: `C:/project/offerlab-vue/package.json`

- [ ] **Step 1: Write frontend guard test**

Create `scripts/test-interest-feed-positioning.mjs`:

```js
import assert from 'node:assert/strict'
import fs from 'node:fs'

const read = (path) => fs.readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

const types = read('src/api/types.ts')
const userApi = read('src/api/user.ts')
const intentForm = read('src/components/user/IntentForm.vue')
const home = read('src/views/HomeView.vue')
const pkg = JSON.parse(read('package.json'))

for (const field of ['interestTopics', 'interestTags', 'contentPreferences']) {
  assert.match(types, new RegExp(`${field}\\??:`), `UserIntent must declare ${field}`)
  assert.match(userApi, new RegExp(field), `userApi must preserve ${field}`)
  assert.match(intentForm, new RegExp(field), `IntentForm must edit ${field}`)
}

assert.match(intentForm, /兴趣|频道|内容偏好/, 'IntentForm should present generalized community interests')
assert.match(home, /兴趣|社区|推荐理由/, 'Home recommendation copy should explain interest/community matching')
assert.match(pkg.scripts['test:guards'], /test:interest-feed-positioning/, 'test:guards must include interest feed guard')

console.log('interest feed positioning guard passed')
```

- [ ] **Step 2: Add npm script**

In `package.json`, add:

```json
"test:interest-feed-positioning": "node scripts/test-interest-feed-positioning.mjs"
```

Also include `npm run test:interest-feed-positioning` inside `test:guards`.

- [ ] **Step 3: Run guard and verify it fails before implementation**

Run:

```powershell
npm run test:interest-feed-positioning
```

Expected: FAIL because the new fields and copy are not fully present yet.

- [ ] **Step 4: Extend frontend `UserIntent` type**

In `src/api/types.ts`, add:

```ts
interestTopics?: string[]
interestTags?: string[]
contentPreferences?: string[]
```

to `UserIntent`.

- [ ] **Step 5: Preserve fields in `userApi.updateIntent`**

In `src/api/user.ts`, ensure the update payload includes:

```ts
interestTopics: intent.interestTopics || [],
interestTags: intent.interestTags || [],
contentPreferences: intent.contentPreferences || [],
```

while keeping existing `targetCompanies`, `targetPositions`, `targetCity`/`expectedCity`, `yearsOfExp`, and `techStack`.

- [ ] **Step 6: Update `IntentForm.vue`**

Add three editable arrays to the form model:

```ts
interestTopics: [] as string[],
interestTags: [] as string[],
contentPreferences: [] as string[],
```

Expose them as compact checkbox/chip controls using predefined options:

```ts
const interestTopicOptions = ['职场成长', '城市生活', '效率工具', '学习成长', '消费决策', '生活方式']
const interestTagOptions = ['经验复盘', '避坑指南', '清单推荐', '工具分享', '深度讨论', '新手友好']
const contentPreferenceOptions = ['图文笔记', '长文经验', '问答讨论', '趋势观察']
```

Keep existing job-search fields in the same form as optional context.

- [ ] **Step 7: Update home recommendation copy**

In `HomeView.vue`, change the recommendation tab description from engineering/job-search-only wording to wording that includes community interests and recommendation reasons. Preserve `activeFeed === 'recommend'` behavior and `show-recommend-feedback`.

- [ ] **Step 8: Run frontend focused checks**

Run:

```powershell
npm run test:interest-feed-positioning
npm run typecheck
```

Expected: PASS.

---

## Task 3: Main Integration Review

**Files:**
- Review only files modified by Task 1 and Task 2.

- [ ] **Step 1: Check changed files**

Run:

```powershell
cd C:/project/offerlab-java
git status --short

cd C:/project/offerlab-vue
git status --short
```

Expected: new changes are limited to the task scopes. Pre-existing dirty files may remain, but no unrelated new edits should appear.

- [ ] **Step 2: Verify field-name consistency**

Confirm all backend and frontend code uses exactly:

```text
interestTopics
interestTags
contentPreferences
```

- [ ] **Step 3: Run focused backend tests**

Run:

```powershell
cd C:/project/offerlab-java
mvn -pl community-archtest -Dtest=UserIntentDTOJsonTest test
mvn -pl community-bootstrap -Dtest=FeedFacadeVisibilityTest test
```

Expected: PASS.

- [ ] **Step 4: Run focused frontend tests**

Run:

```powershell
cd C:/project/offerlab-vue
npm run test:interest-feed-positioning
npm run typecheck
```

Expected: PASS.

- [ ] **Step 5: Decide whether broader verification is affordable**

If focused checks pass and time allows, run:

```powershell
cd C:/project/offerlab-vue
npm run test:guards
```

Expected: PASS. If it fails on unrelated pre-existing guards, report exact failing guard and do not expand scope.

---

## Self-Review Notes

- Spec coverage: P0/P1 interest loop maps to backend DTO, backend recommendation scoring/reasons, frontend settings, frontend home copy, and focused tests.
- Scope control: media posts, collections, creator analytics, governance expansion, and ML recommendation are explicitly excluded.
- Parallel safety: backend and frontend subtasks have disjoint file scopes and independent verification commands.
- Risk: both repositories currently have many pre-existing dirty files, so execution must avoid unrelated edits and should review `git diff --` for every touched file before finalizing.
