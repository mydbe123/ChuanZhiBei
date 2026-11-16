package com.zhixue.engine.rag;

import com.zhixue.engine.common.BusinessException;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.AppProperties;
import com.zhixue.engine.rag.KnowledgeDtos.DocumentView;
import com.zhixue.engine.rag.KnowledgeDtos.Overview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {

    private final DocumentRepository documents;
    private final DocChunkRepository chunks;
    private final DocumentExtractor extractor;
    private final TextSplitter splitter;
    private final Tokenizer tokenizer;
    private final KnowledgeIndex index;
    private final AppProperties properties;
    private final Json json;

    public KnowledgeService(DocumentRepository documents, DocChunkRepository chunks,
                            DocumentExtractor extractor, TextSplitter splitter, Tokenizer tokenizer,
                            KnowledgeIndex index, AppProperties properties, Json json) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.splitter = splitter;
        this.tokenizer = tokenizer;
        this.index = index;
        this.properties = properties;
        this.json = json;
    }

    public Overview overview(long userId) {
        List<Document> list = documents.findAccessible(userId);
        List<DocumentView> views = list.stream().map(DocumentView::of).toList();
        int chunkTotal = list.stream().mapToInt(Document::getChunkCount).sum();
        int charTotal = list.stream().mapToInt(Document::getCharCount).sum();
        return new Overview(views, views.size(), chunkTotal, charTotal);
    }

    @Transactional
    public DocumentView ingestFile(long userId, String title, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("请选择需要上传的文件");
        }
        String text = extractor.extract(fileName, bytes);
        return persist(userId, resolveTitle(title, fileName), fileName, extractor.sourceType(fileName),
                text, bytes.length);
    }

    @Transactional
    public DocumentView ingestText(long userId, String title, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("文档内容不能为空");
        }
        String safeTitle = title == null || title.isBlank() ? "未命名笔记" : title.trim();
        return persist(userId, safeTitle, null, "TEXT", content, content.getBytes().length);
    }

    /** 供启动时的示例知识库导入使用（userId = null 表示公共知识） */
    @Transactional
    public void ingestPublic(String title, String sourceType, String text) {
        if (documents.findFirstByUserIdIsNullAndTitle(title).isPresent()) {
            return;
        }
        persist(null, title, null, sourceType, text, text.getBytes().length);
    }

    private DocumentView persist(Long userId, String title, String fileName, String sourceType,
                                 String text, int byteSize) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("文档内容为空或无法解析出文本");
        }
        List<String> pieces = splitter.split(text, properties.rag().chunkSize(), properties.rag().chunkOverlap());
        if (pieces.isEmpty()) {
            throw new BusinessException("文档过短，至少需要 50 个字符");
        }
        Document document = new Document();
        document.setUserId(userId);
        document.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        document.setFileName(fileName);
        document.setSourceType(sourceType);
        document.setByteSize(byteSize);
        document.setCharCount(text.length());
        document.setChunkCount(pieces.size());
        document.setSummary(text.replaceAll("\\s+", " ").substring(0, Math.min(160, text.length())));
        documents.save(document);

        List<DocChunk> entities = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            Map<String, Integer> tf = tokenizer.termFrequencies(piece);
            Map<String, Double> vector = tokenizer.toWeightedVector(tf);
            entities.add(DocChunk.of(document.getId(), i, piece, json.write(vector), tf.size(),
                    keywords(vector)));
        }
        chunks.saveAll(entities);
        invalidate(document.getUserId());
        return DocumentView.of(document);
    }

    @Transactional
    public void delete(long userId, long documentId) {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new BusinessException(404, "文档不存在"));
        if (document.getUserId() == null) {
            throw new BusinessException(403, "公共示例文档不可删除");
        }
        if (document.getUserId() != userId) {
            throw new BusinessException(403, "无权删除他人的文档");
        }
        chunks.deleteByDocumentId(documentId);
        documents.delete(document);
        index.invalidate(userId);
    }

    public List<RetrievalHit> search(long userId, String query, Integer topK) {
        int limit = topK == null ? properties.rag().topK() : Math.max(1, Math.min(12, topK));
        return index.search(userId, query, limit);
    }

    private void invalidate(Long userId) {
        if (userId == null) {
            index.invalidateAll();
        } else {
            index.invalidate(userId);
        }
    }

    private String keywords(Map<String, Double> vector) {
        return vector.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .map(e -> e.getKey().substring(2))
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String resolveTitle(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (fileName == null || fileName.isBlank()) {
            return "未命名文档";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
