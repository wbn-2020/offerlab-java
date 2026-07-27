import { createHash } from 'node:crypto'

export const normalizeMigrationContent = (content) =>
  content
    .toString('utf8')
    .replace(/^\uFEFF/, '')
    .replace(/\r\n|\r/g, '\n')

export const migrationContentSha256 = (content) =>
  createHash('sha256')
    .update(Buffer.from(normalizeMigrationContent(content), 'utf8'))
    .digest('hex')
