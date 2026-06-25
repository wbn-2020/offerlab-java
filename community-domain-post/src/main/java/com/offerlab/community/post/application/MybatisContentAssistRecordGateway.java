package com.offerlab.community.post.application;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class MybatisContentAssistRecordGateway implements ContentAssistRecordGateway {

    private final ContentAssistRecordMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    public void save(ContentAssistAuditRecord record) {
        mapper.insert(idGenerator.nextId(), record.uid(), record.scene(), record.provider(), record.status(),
                record.domain(), record.contentLength(), record.contentHash(), record.promptTokens(),
                record.completionTokens(), record.estimatedCostMicros(), record.errorCode());
    }
}
