package com.offerlab.community.feed.application;

import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.feed.infrastructure.persistence.mapper.RecommendFeedNewCreatorSupportStatMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.RecommendFeedNewCreatorSupportStatPO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendFeedNewCreatorSupportStatsService implements RecommendFeedNewCreatorSupportRecorder {

    private final RecommendFeedNewCreatorSupportStatMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    private volatile boolean tableReadyConfirmed;

    @Override
    public void recordRecommendFeedResponse(Long viewerUid, Integer domain, int deliveredItemCount, int supportHitItemCount) {
        if (deliveredItemCount <= 0) {
            return;
        }
        if (!tableReady()) {
            return;
        }
        try {
            RecommendFeedNewCreatorSupportStatPO record = new RecommendFeedNewCreatorSupportStatPO();
            record.setId(idGenerator.nextId());
            record.setViewerUid(viewerUid);
            record.setDomain(domain);
            record.setDeliveredItemCount(deliveredItemCount);
            record.setSupportHitItemCount(supportHitItemCount);
            mapper.insert(record);
        } catch (RuntimeException e) {
            log.warn("recommend feed new creator support stats write failed, viewerUid={}, domain={}, deliveredItemCount={}, supportHitItemCount={}",
                    LogMask.id(viewerUid), domain, deliveredItemCount, supportHitItemCount, e);
        }
    }

    private boolean tableReady() {
        if (tableReadyConfirmed) {
            return true;
        }
        try {
            if (mapper.tableExists() > 0) {
                tableReadyConfirmed = true;
                return true;
            }
        } catch (RuntimeException e) {
            log.debug("recommend feed new creator support stats table check failed", e);
        }
        return false;
    }
}
