import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal ReAct-style agent loop.
 *
 * Each turn we send the LLM the running scratchpad ("Thought/Action/
 * Observation" transcript so far) plus the user's original query, parse
 * exactly one Action out of the LLM's response, call the corresponding
 * tool, append the Observation, and loop.
 *
 * Two hard budgets guard the loop: a step budget (iterations) and a token
 * budget (estimated prompt+response size). Either one stops the run, as
 * does an explicit "Final Answer:" from the model.
 */
public class AgentDriver {

    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path promptTemplate;
    private final int stepBudget;
    private final int tokenBudget;

    private int stepsUsed;
    private int tokensUsed;
    private String stopReason = "(not run)";
    private final StringBuilder trace = new StringBuilder();
    private final List<String> toolCalls = new ArrayList<>();

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget) {
        this(llm, promptTemplate, stepBudget, Integer.MAX_VALUE);
    }

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget, int tokenBudget) {
        this.llm = llm;
        this.promptTemplate = promptTemplate;
        this.stepBudget = stepBudget;
        this.tokenBudget = tokenBudget;
        registerTool(new ListBooksTool());
        registerTool(new SearchBooksTool());
    }

    public void registerTool(Tool t) {
        tools.put(t.name(), t);
    }

    public Map<String, Tool> getTools() {
        return tools;
    }

    /**
     * Run the loop. Returns the final scratchpad (transcript of all
     * thoughts, actions, observations, plus the last LLM response).
     */
    public String run(String userQuery) throws IOException {
        reset();
        String template = Files.readString(promptTemplate);
        StringBuilder scratch = new StringBuilder();
        String lastResponse = "";
        for (int step = 0; step < stepBudget; step++) {
            String prompt = template
                    .replace("{query}", userQuery)
                    .replace("{scratchpad}", scratch.toString());
            lastResponse = llm.complete(prompt);
            stepsUsed = step + 1;
            tokensUsed += tokens(prompt) + tokens(lastResponse);
            scratch.append(lastResponse).append('\n');
            trace.append("=== step ").append(stepsUsed).append(" (cumulative tokens ")
                 .append(tokensUsed).append(") ===\n")
                 .append(lastResponse.strip()).append('\n');

            if (shouldStop(lastResponse, stepsUsed)) {
                stopReason = stopReasonFor(lastResponse);
                break;
            }
            Map.Entry<String, String> action = parseAction(lastResponse);
            if (action == null) {
                stopReason = "no Action/Action Input pair parsed";
                trace.append("ACTION: (none parsed)\n");
                scratch.append("Observation: (no action parsed; stopping)\n");
                break;
            }
            Tool t = tools.get(action.getKey());
            String obs = (t == null)
                    ? "ERROR: unknown tool " + action.getKey()
                    : t.call(action.getValue());
            toolCalls.add(action.getKey() + "(" + action.getValue() + ")");
            trace.append("ACTION: ").append(action.getKey())
                 .append("  ARG: ").append(action.getValue()).append('\n')
                 .append("OBSERVATION: ").append(abbreviate(obs)).append('\n');
            scratch.append("Observation: ").append(obs).append('\n');
        }
        if (stopReason.equals("(not run)")) {
            stopReason = stopReasonFor(lastResponse);
        }
        trace.append("STOP: ").append(stopReason)
             .append("  [steps=").append(stepsUsed)
             .append(" tokens=").append(tokensUsed)
             .append(" toolCalls=").append(toolCalls.size()).append("]\n");
        return scratch.toString();
    }

    /**
     * Stop once the model has emitted a "Final Answer:" line, or either
     * budget is exhausted. {@code step} is the 1-based count of steps used.
     */
    protected boolean shouldStop(String lastResponse, int step) {
        return (lastResponse != null && lastResponse.contains("Final Answer:"))
                || step >= stepBudget
                || tokensUsed >= tokenBudget;
    }

    private String stopReasonFor(String lastResponse) {
        if (lastResponse != null && lastResponse.contains("Final Answer:")) {
            return "final answer";
        }
        if (tokensUsed >= tokenBudget) {
            return "token budget (" + tokenBudget + ") exhausted";
        }
        if (stepsUsed >= stepBudget) {
            return "step budget (" + stepBudget + ") exhausted";
        }
        return "stopped";
    }

    private void reset() {
        stepsUsed = 0;
        tokensUsed = 0;
        stopReason = "(not run)";
        trace.setLength(0);
        toolCalls.clear();
    }

    private static final Pattern ACTION_LINE =
            Pattern.compile("^Action:\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*$", Pattern.MULTILINE);
    private static final Pattern INPUT_LINE =
            Pattern.compile("^Action Input:\\s*(.*)$", Pattern.MULTILINE);

    /**
     * Parse the first Action / Action Input pair out of the LLM reply.
     * Returns null when no Action line is present, so the loop can stop.
     */
    protected Map.Entry<String, String> parseAction(String response) {
        if (response == null) {
            return null;
        }
        Matcher action = ACTION_LINE.matcher(response);
        if (!action.find()) {
            return null;
        }
        Matcher input = INPUT_LINE.matcher(response);
        String arg = input.find() ? input.group(1).trim() : "";
        return new AbstractMap.SimpleEntry<>(action.group(1).trim(), arg);
    }

    /** Rough token estimate: ~4 characters per token. */
    private static int tokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    private static String abbreviate(String obs) {
        String flat = obs == null ? "" : obs.replaceAll("\\s+", " ").strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + " …(" + flat.length() + " chars)";
    }

    public int stepsUsed() {
        return stepsUsed;
    }

    public int tokensUsed() {
        return tokensUsed;
    }

    public String stopReason() {
        return stopReason;
    }

    public List<String> toolCalls() {
        return toolCalls;
    }

    public String traceLog() {
        return trace.toString();
    }
}