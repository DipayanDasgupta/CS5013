import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

/** {@link LlmClient} backed by a local Ollama model. No API key involved. */
public class OllamaLlm implements LlmClient {

    private final ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl(System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434"))
            .modelName(System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.2:3b"))
            .temperature(0.0)
            .build();

    @Override
    public String complete(String prompt) {
        return model.generate(prompt);
    }

    @Override
    public String toString() {
        return "OllamaLlm[" + System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.2:3b") + "]";
    }
}