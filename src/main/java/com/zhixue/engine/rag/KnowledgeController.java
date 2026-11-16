package com.zhixue.engine.rag;

import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.rag.KnowledgeDtos.Citation;
import com.zhixue.engine.rag.KnowledgeDtos.DocumentView;
import com.zhixue.engine.rag.KnowledgeDtos.Overview;
import com.zhixue.engine.rag.KnowledgeDtos.SearchRequest;
import com.zhixue.engine.rag.KnowledgeDtos.TextIngestRequest;
import com.zhixue.engine.security.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/documents")
    public ApiResult<Overview> documents() {
        return ApiResult.ok(knowledgeService.overview(UserContext.requireUserId()));
    }

    @PostMapping("/documents")
    public ApiResult<DocumentView> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "title", required = false) String title)
            throws IOException {
        return ApiResult.ok(knowledgeService.ingestFile(UserContext.requireUserId(), title,
                file.getOriginalFilename(), file.getBytes()));
    }

    @PostMapping("/documents/text")
    public ApiResult<DocumentView> uploadText(@RequestBody TextIngestRequest request) {
        return ApiResult.ok(knowledgeService.ingestText(UserContext.requireUserId(),
                request.title(), request.content()));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        knowledgeService.delete(UserContext.requireUserId(), id);
        return ApiResult.ok();
    }

    @PostMapping("/search")
    public ApiResult<List<Citation>> search(@RequestBody SearchRequest request) {
        List<Citation> citations = knowledgeService
                .search(UserContext.requireUserId(), request.query(), request.topK())
                .stream()
                .map(Citation::of)
                .toList();
        return ApiResult.ok(citations);
    }
}
