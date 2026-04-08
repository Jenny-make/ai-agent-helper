package com.example.customerservice.service;

import com.example.customerservice.model.knowledge.FeishuKnowledgeDocument;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;

public interface FeishuKnowledgeSourceClient {

    FeishuKnowledgeDocument fetch(FeishuKnowledgeSource source);
}
