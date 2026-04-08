package com.example.customerservice.service;

import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import java.util.List;

public interface FeishuKnowledgeSourceDiscoveryService {

    List<FeishuKnowledgeSource> expand(List<FeishuKnowledgeSource> sources);
}
