import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

/**
 * Real semantic embedder: all-MiniLM-L6-v2, bundled inside
 * langchain4j-embeddings-all-minilm-l6-v2 and executed locally on ONNX
 * Runtime -- no network, no API key, 384 dimensions.
 */
public class MiniLmEmbedder implements Embedder {

    public static final int DIM = 384;

    private static final AllMiniLmL6V2EmbeddingModel MODEL = new AllMiniLmL6V2EmbeddingModel();

    @Override
    public float[] embed(String text) {
        Embedding embedding = MODEL.embed(text == null ? "" : text).content();
        embedding.normalize();
        return embedding.vector();
    }

    @Override
    public String toString() {
        return "all-MiniLM-L6-v2[dim=" + DIM + "]";
    }
}