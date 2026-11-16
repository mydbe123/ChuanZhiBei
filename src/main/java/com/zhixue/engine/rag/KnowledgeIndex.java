package com.zhixue.engine.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhixue.engine.common.Json;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量向量索引。
 * <p>
 * 切片向量以稀疏词权重形式持久化在 MySQL，检索时在内存中构建倒排统计（DF/IDF），
 * 采用「TF-IDF 余弦相似度 + 关键词覆盖率」的混合打分，并用 MMR 做结果去冗余。
 * 这样无需外接向量数据库即可获得可解释、可复现的 RAG 检索效果。
 */
@Component
public class KnowledgeIndex {

    private static final double VECTOR_WEIGHT = 0.62;
    private static final double KEYWORD_WEIGHT = 0.38;
    private static final double MMR_LAMBDA = 0.78;

    private final DocChunkRepository chunkRepository;
    private final Tokenizer tokenizer;
    private final Json json;
    private final Map<Long, Snapshot> cache = new ConcurrentHashMap<>();

    public KnowledgeIndex(DocChunkRepository chunkRepository, Tokenizer tokenizer, Json json) {
        this.chunkRepository = chunkRepository;
        this.tokenizer = tokenizer;
        this.json = json;
    }

    public List<RetrievalHit> search(long userId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Snapshot snapshot = cache.computeIfAbsent(userId, this::build);
        if (snapshot.entries.isEmpty()) {
            return List.of();
        }
        Map<String, Double> queryVector = snapshot.queryVector(tokenizer.toWeightedVector(
                tokenizer.termFrequencies(query)));
        if (queryVector.isEmpty()) {
            return List.of();
        }
        double queryIdfSum = queryVector.keySet().stream()
                .mapToDouble(snapshot::idfOf)
                .sum();
        return snapshot.rank(queryVector, queryIdfSum, Math.max(1, topK));
    }

    public void invalidate(long userId) {
        cache.remove(userId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Snapshot build(long userId) {
        List<ChunkRow> rows = chunkRepository.findAccessibleChunks(userId);
        Map<String, Integer> df = new HashMap<>();
        List<Entry> entries = new ArrayList<>(rows.size());
        double totalLength = 0;

        for (ChunkRow row : rows) {
            Map<String, Double> raw = parse(row.sparseVector());
            if (raw.isEmpty()) {
                continue;
            }
            raw.keySet().forEach(term -> df.merge(term, 1, Integer::sum));
            double length = raw.values().stream().mapToDouble(Double::doubleValue).sum();
            totalLength += length;
            entries.add(new Entry(row.chunkId(), row.documentId(), row.documentTitle(), row.seq(),
                    row.content(), raw, length));
        }
        int docCount = Math.max(1, entries.size());
        Map<String, Double> idf = new HashMap<>(df.size());
        df.forEach((term, freq) -> idf.put(term,
                Math.log(1 + (docCount - freq + 0.5) / (freq + 0.5))));
        double idfUnseen = Math.log(1 + (docCount + 0.5) / 0.5);
        double avgLength = entries.isEmpty() ? 1 : totalLength / entries.size();

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            entries.set(i, entry.withVector(normalizeWeighted(entry.raw, idf, idfUnseen)));
        }
        return new Snapshot(entries, idf, idfUnseen, avgLength);
    }

    private Map<String, Double> parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return json.mapper().readValue(rawJson, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Double> normalizeWeighted(Map<String, Double> raw, Map<String, Double> idf,
                                                  double idfUnseen) {
        Map<String, Double> weighted = new LinkedHashMap<>(raw.size());
        double norm = 0;
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double value = e.getValue() * idf.getOrDefault(e.getKey(), idfUnseen);
            weighted.put(e.getKey(), value);
            norm += value * value;
        }
        double sqrt = Math.sqrt(norm);
        if (sqrt <= 0) {
            return weighted;
        }
        weighted.replaceAll((k, v) -> v / sqrt);
        return weighted;
    }

    private record Entry(long chunkId, long documentId, String title, int seq, String content,
                         Map<String, Double> raw, double length, Map<String, Double> vector) {

        Entry(long chunkId, long documentId, String title, int seq, String content,
              Map<String, Double> raw, double length) {
            this(chunkId, documentId, title, seq, content, raw, length, Map.of());
        }

        Entry withVector(Map<String, Double> vector) {
            return new Entry(chunkId, documentId, title, seq, content, raw, length, vector);
        }

        double cosine(Map<String, Double> query) {
            if (vector.isEmpty() || query.isEmpty()) {
                return 0;
            }
            Map<String, Double> small = vector.size() <= query.size() ? vector : query;
            Map<String, Double> large = small == vector ? query : vector;
            double dot = 0;
            for (Map.Entry<String, Double> e : small.entrySet()) {
                Double other = large.get(e.getKey());
                if (other != null) {
                    dot += e.getValue() * other;
                }
            }
            return dot;
        }

        double similarity(Entry other) {
            return cosine(other.vector);
        }
    }

    private static final class Snapshot {

        private final List<Entry> entries;
        private final Map<String, Double> idf;
        private final double idfUnseen;
        private final double avgLength;

        private Snapshot(List<Entry> entries, Map<String, Double> idf, double idfUnseen, double avgLength) {
            this.entries = entries;
            this.idf = idf;
            this.idfUnseen = idfUnseen;
            this.avgLength = avgLength;
        }

        double idfOf(String term) {
            return idf.getOrDefault(term, idfUnseen);
        }

        Map<String, Double> queryVector(Map<String, Double> rawQuery) {
            return normalizeQuery(rawQuery);
        }

        private Map<String, Double> normalizeQuery(Map<String, Double> rawQuery) {
            Map<String, Double> weighted = new LinkedHashMap<>(rawQuery.size());
            double norm = 0;
            for (Map.Entry<String, Double> e : rawQuery.entrySet()) {
                double value = e.getValue() * idfOf(e.getKey());
                weighted.put(e.getKey(), value);
                norm += value * value;
            }
            double sqrt = Math.sqrt(norm);
            if (sqrt <= 0) {
                return weighted;
            }
            weighted.replaceAll((k, v) -> v / sqrt);
            return weighted;
        }

        List<RetrievalHit> rank(Map<String, Double> queryVector, double queryIdfSum, int topK) {
            List<Scored> scored = new ArrayList<>();
            for (Entry entry : entries) {
                double cosine = entry.cosine(queryVector);
                double matchedIdf = 0;
                for (String term : queryVector.keySet()) {
                    if (entry.raw.containsKey(term)) {
                        matchedIdf += idfOf(term);
                    }
                }
                double coverage = queryIdfSum <= 0 ? 0 : matchedIdf / queryIdfSum;
                // 长切片做轻微长度惩罚，避免超长片段"霸榜"
                double lengthPenalty = 1 - Math.min(0.15,
                        Math.abs(entry.length - avgLength) / Math.max(avgLength, 1) * 0.15);
                double score = (VECTOR_WEIGHT * cosine + KEYWORD_WEIGHT * coverage) * lengthPenalty;
                if (score > 0.02) {
                    scored.add(new Scored(entry, score, cosine, coverage));
                }
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
            List<Scored> pool = new ArrayList<>(scored.subList(0, Math.min(scored.size(), topK * 4)));

            List<Scored> selected = new ArrayList<>();
            while (selected.size() < topK && !pool.isEmpty()) {
                Scored best = null;
                double bestValue = Double.NEGATIVE_INFINITY;
                for (Scored candidate : pool) {
                    double maxSim = 0;
                    for (Scored chosen : selected) {
                        maxSim = Math.max(maxSim, candidate.entry.similarity(chosen.entry));
                    }
                    double value = MMR_LAMBDA * candidate.score - (1 - MMR_LAMBDA) * maxSim;
                    if (value > bestValue) {
                        bestValue = value;
                        best = candidate;
                    }
                }
                if (best == null) {
                    break;
                }
                selected.add(best);
                pool.remove(best);
            }
            return selected.stream()
                    .map(s -> new RetrievalHit(s.entry.chunkId, s.entry.documentId, s.entry.title,
                            s.entry.seq, s.entry.content, round(s.score), round(s.vectorScore),
                            round(s.keywordScore)))
                    .toList();
        }

        private double round(double value) {
            return Math.round(value * 1000d) / 1000d;
        }
    }

    private record Scored(Entry entry, double score, double vectorScore, double keywordScore) {
    }
}
