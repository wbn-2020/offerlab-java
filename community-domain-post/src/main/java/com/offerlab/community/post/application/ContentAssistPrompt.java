package com.offerlab.community.post.application;

import java.util.List;

record ContentAssistPrompt(Integer domain,
                           Integer postType,
                           String title,
                           String content,
                           List<String> tagNames,
                           String assistContext,
                           String assistTemplateCode) {
}
