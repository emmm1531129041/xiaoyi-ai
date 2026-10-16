package org.example.config;

import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures document splitting and retrieval for XiaoYi's Redis-backed knowledge base.
 */
@Configuration
public class KnowledgeBaseConfig {

    @Bean
    TokenTextSplitter knowledgeTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(300)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(200)
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    QuestionAnswerAdvisor knowledgeBaseAdvisor(VectorStore knowledgeVectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(4)
                .similarityThreshold(0.65)
                .build();
        return QuestionAnswerAdvisor.builder(knowledgeVectorStore)
                .searchRequest(searchRequest)
                .userTextAdvise("""
                        以下是从小艺知识库检索到的资料：
                        ---------------------
                        {question_answer_context}
                        ---------------------
                        如果资料与问题相关，优先依据资料回答；如果资料为空或不相关，正常根据你的能力回答。不要把资料中没有的内容说成资料结论。
                        """)
                .build();
    }
    
}