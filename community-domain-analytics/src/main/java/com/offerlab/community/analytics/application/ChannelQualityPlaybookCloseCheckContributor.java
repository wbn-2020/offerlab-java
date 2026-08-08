package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernancePlaybookMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChannelQualityPlaybookCloseCheckContributor implements ChannelQualityRiskCaseCloseCheckProvider {
    private final ChannelQualityGovernancePlaybookMapper mapper;

    @Override
    public String code() {
        return "V44_PLAYBOOK";
    }

    @Override
    public int contractVersion() {
        return 1;
    }

    @Override
    public int order() {
        return 440;
    }

    @Override
    public ChannelQualityRiskCaseCloseCheckResult evaluate(ChannelQualityRiskCaseCloseCheckContext context) {
        try {
            if (context == null || context.riskCase() == null || context.riskCase().id() == null
                    || context.riskCase().id() <= 0 || mapper.tableCount() != 5) {
                return block("V44_PLAYBOOK_UNAVAILABLE");
            }
            List<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> bindings =
                    mapper.listCasePlaybooks(context.riskCase().id());
            for (ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding : bindings) {
                if (binding == null || binding.getBindingStatus() == null) {
                    return block("V44_PLAYBOOK_DATA_INVALID");
                }
                if ("ACCEPTED".equals(binding.getBindingStatus())) {
                    return block("V44_ACCEPTED_PLAYBOOK_IN_PROGRESS");
                }
                if ("COMPLETED".equals(binding.getBindingStatus())
                        && (binding.getCompletionEvaluationHash() == null
                        || !binding.getCompletionEvaluationHash().matches("^sha256:[0-9a-f]{64}$"))) {
                    return block("V44_COMPLETION_EVALUATION_INVALID");
                }
                if (!List.of("PROPOSED", "COMPLETED", "WAIVED").contains(binding.getBindingStatus())
                        && !"ACCEPTED".equals(binding.getBindingStatus())) {
                    return block("V44_PLAYBOOK_DATA_INVALID");
                }
            }
            return new ChannelQualityRiskCaseCloseCheckResult("BLOCKING", "PASS",
                    "V44_PLAYBOOK_PASS", "All accepted V44 playbooks are terminal and evaluated.");
        } catch (BizException ex) {
            return block("V44_PLAYBOOK_UNAVAILABLE");
        } catch (RuntimeException ex) {
            return block("V44_PLAYBOOK_UNAVAILABLE");
        }
    }

    private static ChannelQualityRiskCaseCloseCheckResult block(String reason) {
        return new ChannelQualityRiskCaseCloseCheckResult("BLOCKING", "FAIL", reason,
                "V44 playbook close-check is not satisfied.");
    }
}
