package com.github.zacharydhamilton.processor;

import java.util.Arrays;
import java.util.List;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;

public class EmbeddingUtils {
    private static String key = System.getenv("OPENAI_API_KEY");
    private static String model = "text-embedding-ada-002";

    public static List<Embedding> create_embedding(String text) {
        // TODO Add some kind of throttling protection
        OpenAiService service = new OpenAiService(key);
        EmbeddingRequest request = EmbeddingRequest.builder()
            .input(Arrays.asList(text))
            .model(model)
            .build();
        List<Embedding> embeddings = service.createEmbeddings(request).getData();
        return embeddings;
    }
}
