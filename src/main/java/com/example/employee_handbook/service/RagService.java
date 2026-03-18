package com.example.employee_handbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:policy.pdf")
    private Resource pdfFile;

    public String askAI(String prompt) {

        String template = """
                You are an AI assistant helping an employee.
                
                Rules:
                - Use ONLY the information provided in the context
                - You MAY rephrase, summarize, and explain in natural language
                - Do NOT introduce new concepts or facts
                - If multiple context sections are relevant, combine them into a single explanation
                - If the answer is not present, say "I don't know"
                
                Context:
                {context}
                
                Answer in a friendly, conversational tone.
                """;

//        Build Context from Documents

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(prompt)
                .topK(5)
                .similarityThreshold(0.4)
                        .filterExpression("file_name == 'policy.pdf'")
                .build());

//        Convert list → stream
        String context = documents.stream()
                .map(document -> document.getText())// Extract text only from each document
                .collect(Collectors.joining("\n\n"));// Combine all texts into ONE string

//        Inject Context into Prompt
        PromptTemplate promptTemplate = new PromptTemplate(template);
        String systemPrompt = promptTemplate.render(Map.of("context", context));// replaces {context} with actual text

//          Send to LLM
        return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .advisors(
                        new SimpleLoggerAdvisor()
                )
                .call()
                .content();
    }

//    ETL pipeline for RAG
    public void ingestPdfToVectorStore() {

//        Read the PDF
        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfFile);
        List<Document> page = reader.get();

//        Split into Chunks
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(120)
                .build();

        List<Document> chunks = splitter.apply(page);

//        to see chunks
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("Chunk " + i + ":\n" + chunks.get(i).getText());
            System.out.println("-----");
        }
        vectorStore.add(chunks);


    }


}


