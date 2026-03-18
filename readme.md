# 📘 Employee Handbook RAG Bot

An AI-powered employee handbook assistant built with **Spring Boot**, **Spring AI**, **OpenAI embeddings/chat**, **pgvector**, and **PostgreSQL**.

It ingests a PDF handbook on startup, chunks it into searchable documents, stores embeddings in a vector database, retrieves relevant policy snippets for a user question, and generates grounded answers using Retrieval-Augmented Generation (RAG).

---

## 🚀 Features

- PDF ingestion on startup using `CommandLineRunner`
- Reads handbook content using `PagePdfDocumentReader`
- Splits content into chunks with `TokenTextSplitter`
- Stores chunk embeddings in **pgvector**
- Semantic search using `VectorStore`
- Chat endpoint for employee questions
- Grounded answers using retrieved handbook context
- Idempotent startup ingestion
- Metadata-based filtering with `file_name = policy.pdf`

---

## 🏗️ Tech Stack

- Java 21
- Spring Boot
- Spring AI
- OpenAI
- PostgreSQL
- pgvector
- Docker
- Maven
- Lombok

---

## 📂 Project Structure

```text
src/main/java/com/example/employee_handbook
├── config
│   ├── AiConfig.java
│   └── PdfIngestionRunner.java
├── controller
│   └── HandbookController.java
├── service
│   └── RagService.java
└── Application.java

src/main/resources
├── application.yaml
└── policy.pdf
```

---

## 🔄 Application Flow

```text
Application Starts
        ↓
Spring creates beans from AiConfig
        ↓
PdfIngestionRunner (CommandLineRunner) runs
        ↓
Read policy.pdf using PagePdfDocumentReader
        ↓
Split PDF text into chunks using TokenTextSplitter
        ↓
Add metadata (file_name, chunk_index)
        ↓
Store chunks + embeddings in pgvector
        ↓
Employee sends question to /ask endpoint
        ↓
RagService performs similarity search in VectorStore
        ↓
Relevant chunks are retrieved
        ↓
Chunks are merged into context
        ↓
Context is injected into system prompt
        ↓
ChatClient sends prompt to LLM
        ↓
LLM returns grounded answer
```

---

## 🧠 RAG Flow Diagram

```text
User Question
   ↓
Embedding Search
   ↓
VectorStore / pgvector
   ↓
Top Matching Chunks
   ↓
PromptTemplate builds context-aware prompt
   ↓
ChatClient calls LLM
   ↓
Final Answer
```

---

## ⚙️ Docker Setup

### `docker-compose.yml`

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    container_name: pgvector-local
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: pgvector_employee_handbook
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  pgdata:
```

### Start Docker

```bash
docker compose up -d
```

### Stop and remove containers + volumes

```bash
docker compose down -v
```

### Remove all containers (optional cleanup)

```bash
docker stop $(docker ps -q)
docker rm -f $(docker ps -aq)
docker volume prune -f
```

---

## 🐘 PostgreSQL / pgvector Commands

Enter the running container:

```bash
docker exec -it pgvector-local psql -U postgres
```

List databases:

```sql
\l
```

Connect to the employee handbook database:

```sql
\c pgvector_employee_handbook
```

Enable pgvector:

```sql
CREATE EXTENSION vector;
```

Verify extensions:

```sql
\dx
```

---

## 🔧 Application Configuration

### `application.yaml`

```yaml
spring:
  application:
    name: employee-handbook

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7

    vectorstore:
      pgvector:
        initialize-schema: true

  datasource:
    url: jdbc:postgresql://localhost:5432/pgvector_employee_handbook
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

logging:
  level:
    org.springframework.ai: DEBUG
```

### What `text-embedding-3-small` means

It is the OpenAI embedding model used to convert handbook text into vectors for semantic search.

Example:

```text
"Remote work is only allowed on Fridays."
        ↓
[0.012, -0.876, 0.145, ...]
```

This vector is what gets stored in pgvector.

---

## 📥 PDF Ingestion

The PDF is loaded on startup by `PdfIngestionRunner`.

### Ingestion responsibilities

- check whether data already exists
- skip if already ingested
- read `policy.pdf`
- split into chunks
- add metadata
- save chunks to vector store

### Example service ingestion logic

```java
public void ingestPdfToVectorStore() {
    PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfFile);
    List<Document> pages = reader.get();

    TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(120)
            .build();

    List<Document> chunks = splitter.apply(pages);

    for (int i = 0; i < chunks.size(); i++) {
        chunks.get(i).getMetadata().put("file_name", "policy.pdf");
        chunks.get(i).getMetadata().put("chunk_index", i);
    }

    vectorStore.add(chunks);
}
```

---

## ▶️ CommandLineRunner

This is startup logic, not business logic.

### Why use it?

Because handbook ingestion should happen once when the application starts.

### Example

```java
@Component
@RequiredArgsConstructor
public class PdfIngestionRunner implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final RagService ragService;

    @Override
    public void run(String... args) {
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("policy")
                        .topK(1)
                        .build()
        );

        if (!existing.isEmpty()) {
            System.out.println("Already ingested, skipping...");
            return;
        }

        System.out.println("Starting PDF ingestion...");
        ragService.ingestPdfToVectorStore();
        System.out.println("PDF ingestion complete");
    }
}
```

---

## 💬 Chat / Ask Endpoint

### Controller

```java
@RestController
@RequiredArgsConstructor
public class HandbookController {

    private final RagService ragService;

    @GetMapping("/ask")
    public String askQuestion(@RequestParam String question) {
        return ragService.askAI(question);
    }
}
```

### Correct request example

```http
GET /ask?question=Can I work from home on Monday?
```

Do not send raw body text to this GET endpoint. It expects a query parameter named `question`.

---

## 🧩 RAG Service

The service does two things:

1. `ingestPdfToVectorStore()` → ETL pipeline
2. `askAI(prompt)` → retrieval + answer generation

### Example retrieval logic

```java
List<Document> documents = vectorStore.similaritySearch(
        SearchRequest.builder()
                .query(prompt)
                .topK(5)
                .similarityThreshold(0.2)
                .filterExpression("file_name == 'policy.pdf'")
                .build()
);
```

### Build context from retrieved documents

```java
String context = documents.stream()
        .map(Document::getText)
        .collect(Collectors.joining("\n\n"));
```

### Inject context into a prompt

```java
PromptTemplate promptTemplate = new PromptTemplate(template);
String systemPrompt = promptTemplate.render(Map.of("context", context));
```

### Call the LLM

```java
return chatClient.prompt()
        .system(systemPrompt)
        .user(prompt)
        .advisors(new SimpleLoggerAdvisor())
        .call()
        .content();
```

---

## 🧠 Prompt Design

Example system template:

```text
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
```

This prevents hallucinations and grounds the answer in the handbook.

---

## ✅ Example Questions

Try these after startup:

```text
Can I work from home on Monday?
What is the expense limit?
What are office hours?
When should leave requests be submitted?
```

### Expected behavior

If the PDF contains:

```text
Remote work is only allowed on Fridays.
```

And the user asks:

```text
Can I work from home on Monday?
```

The answer should be:

```text
No, remote work is only allowed on Fridays.
```

---

## 📡 API Endpoints

### Ask handbook question

```http
GET /ask?question=What is the expense limit?
```

### Example response

```text
The daily expense limit is $50 unless approved by management.
```

---

## 🐛 Common Issues

### 1. `400 Bad Request` on `/ask`

Cause:
- sending raw body to a GET endpoint

Fix:
- use query param:

```http
GET /ask?question=what days can I work remotely?
```

---

### 2. Ingestion not happening

Cause:
- `CommandLineRunner` did not call `ragService.ingestPdfToVectorStore()`

Fix:
- explicitly call it in the runner

---

### 3. `I don't know.` returned even though ingestion worked

Cause:
- retrieval returned empty context
- using `.query("policy")` instead of the actual user prompt
- threshold too high
- filter too restrictive

Fix:
- use `.query(prompt)`
- lower threshold to `0.2`
- verify metadata and filter

---

### 4. `vector` extension not available

Cause:
- plain PostgreSQL image used instead of pgvector image

Fix:
- use:

```yaml
image: pgvector/pgvector:pg17
```

---

### 5. Database does not exist

Fix:

```sql
CREATE DATABASE pgvector_employee_handbook;
```

---

### 6. Port 5432 already allocated

Fix:
- stop old Postgres container
- or change Docker mapping to `5433:5432`

---

## 🔍 Useful Debugging Tips

Print chunks during ingestion:

```java
for (int i = 0; i < chunks.size(); i++) {
    System.out.println("Chunk " + i + ":\n" + chunks.get(i).getText());
    System.out.println("-----");
}
```

Print retrieved docs:

```java
System.out.println("Retrieved docs: " + documents.size());
for (Document doc : documents) {
    System.out.println("DOC: " + doc.getText());
    System.out.println("META: " + doc.getMetadata());
    System.out.println("-----");
}
```

---

## 📈 Why This Project Matters

This project demonstrates:

- ETL for AI systems
- Vector embeddings
- Semantic retrieval
- Basic RAG
- Prompt engineering
- Grounded answer generation
- Spring Boot production structure

It is a strong portfolio and interview project because it combines:
- backend engineering
- databases
- AI application development

---

## 🔮 Future Enhancements

- Return DTO with answer + source chunk
- Include page numbers in metadata
- Add PDF citations in response
- Support multiple handbook PDFs
- Add POST `/ask` endpoint with JSON DTO
- Add frontend UI
- Add authentication
- Deploy on AWS

---

## 👨‍💻 Author

**Umair Ali**  
Backend Engineer | AI Enthusiast  
GitHub: `umairali-bit`
