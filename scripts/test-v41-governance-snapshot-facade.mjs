import fs from 'node:fs';
import path from 'node:path';

const repo = path.resolve('C:/vibe-coding/offerlab/offerlab-java');
const sourceRoot = path.join(
  repo,
  'community-domain-analytics/src/main/java/com/offerlab/community/analytics',
);
const facadePath = path.join(sourceRoot, 'api/ChannelQualityRiskGovernanceSnapshotQueryFacade.java');
const implPath = path.join(
  sourceRoot,
  'application/ChannelQualityRiskGovernanceSnapshotQueryFacadeImpl.java',
);
const mapperPath = path.join(
  sourceRoot,
  'infrastructure/persistence/ChannelQualityRiskGovernanceSnapshotQueryMapper.java',
);

function read(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing required file: ${file}`);
  }
  return fs.readFileSync(file, 'utf8');
}

const facade = read(facadePath);
const impl = read(implPath);
const mapper = read(mapperPath);

for (const [name, text] of [
  ['facade', facade],
  ['implementation', impl],
  ['mapper', mapper],
]) {
  if (text.includes('canonical_payload') || text.includes('canonicalPayload')) {
    throw new Error(`${name} must not read or expose canonical_payload`);
  }
}

if (mapper.includes('@Insert') || mapper.includes('@Update') || mapper.includes('@Delete')) {
  throw new Error('snapshot mapper must remain read-only');
}
if (/\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP)\b/i.test(mapper)) {
  throw new Error('snapshot mapper contains a non-read SQL operation');
}
if (/\bRESOLUTION_SUBMITTED\b/.test(mapper) || /\bRESOLUTION_SUBMITTED\b/.test(impl)) {
  throw new Error('snapshot facade must not derive resolutionSubmittedAt from an unbound latest event');
}

const requiredFields = [
  'schemaVersion',
  'caseId',
  'batchId',
  'domain',
  'caseStatus',
  'caseVersion',
  'coordinationVersion',
  'governanceFactVersion',
  'governanceSnapshotEtag',
  'outcomeType',
  'contentRecoveryState',
  'residualRiskLevel',
  'primaryRootCauseCategory',
  'rootCauseCategories',
  'actionRefs',
  'evidenceRefs',
  'closeSnapshotId',
  'resolutionSubmittedAt',
  'closedAt',
  'payloadSchemaVersion',
  'snapshotDigest',
  'retrospectiveId',
  'retrospectiveStatus',
  'retrospectiveVersion',
  'learningCategories',
  'recurrenceLinks',
  'closeCheckExtensionAvailability',
];
for (const field of requiredFields) {
  if (!new RegExp(`\\b${field}\\b`).test(facade)) {
    throw new Error(`contract field missing: ${field}`);
  }
}

if (!`${facade}\n${impl}`.includes('V41_GOVERNANCE_SNAPSHOT_V1')) {
  throw new Error('V41 schema version is not frozen in the facade');
}
if (!impl.includes('SnapshotTime.unavailable(')) {
  throw new Error('unavailable time fields must be explicit');
}
if (!impl.includes('ETAG_PREFIX = "sha256:"')) {
  throw new Error('governanceSnapshotEtag format is not guarded');
}
if (!/SnapshotTime\s+resolutionSubmittedAt/.test(facade)
  || !/SnapshotTime\s+closedAt/.test(facade)) {
  throw new Error('close time fields must expose explicit availability');
}
for (const unsafeField of [
  'referenceKey',
  'subjectRef',
  'sourceRef',
  'createdByUid',
  'closedByUid',
  'ownerUid',
]) {
  if (new RegExp(`\\b${unsafeField}\\b`).test(facade)) {
    throw new Error(`contract exposes an unsafe field: ${unsafeField}`);
  }
}

console.log('V41 governance snapshot facade static guard: PASS');
