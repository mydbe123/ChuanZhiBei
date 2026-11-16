package com.zhixue.engine.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 启动时导入示例知识库（公共文档），保证 RAG 功能开箱即用。
 */
@Component
public class KnowledgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSeeder.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeSeeder(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:seed/*.md");
            Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            int imported = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String title = filename == null ? "示例文档" : filename.replaceFirst("\\.md$", "");
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                knowledgeService.ingestPublic(title, "MARKDOWN", text);
                imported++;
            }
            if (imported > 0) {
                log.info("示例知识库已就绪，共 {} 篇公共文档", imported);
            }
        } catch (Exception e) {
            log.warn("示例知识库导入失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
