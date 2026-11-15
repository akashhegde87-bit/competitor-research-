package competitor_research;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final String MODEL_ID = "gemini-3.8-flash";
    private static final int MAX_HISTORY = 10;

    private final Client client;
    private final ObjectMapper objectMapper;
    private final WebSearchService webSearchService;

    private final Map<String, CompetitorReport> reportCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_HISTORY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompetitorReport> eldest) {
                    return size() > MAX_HISTORY;
                }
            }
    );

    public GeminiService(ObjectMapper objectMapper, WebSearchService webSearchService) {
        this.objectMapper = objectMapper;
        this.webSearchService = webSearchService;

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is missing. Set it in Windows and restart IntelliJ."
            );
        }
        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
    }

    public CompetitorReport researchCompany(String company, boolean refresh) {
        if (company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be empty. Provide a name like ?company=Zoho.");
        }

        String cleanCompany = company.trim();
        String cacheKey = cleanCompany.toLowerCase();

        if (!refresh && reportCache.containsKey(cacheKey)) {
            return reportCache.get(cacheKey);
        }

        WebSearchService.SearchContext searchContext = webSearchService.searchCompany(cleanCompany);

        String sourceNote = searchContext.liveGrounded()
                ? "Grounded with live web search results (" + searchContext.sources().size() + " sources)"
                : "AI-generated analysis (live web search unavailable)";

        String trimmedSnippets = searchContext.formattedSnippets();
        if (trimmedSnippets != null && trimmedSnippets.length() > 6000) {
            trimmedSnippets = trimmedSnippets.substring(0, 6000);
        }

        String groundingSection = searchContext.liveGrounded()
                ? "Use these live web snippets as primary context:\n" + trimmedSnippets
                : "Rely on general business knowledge.";

        String prompt = """
                Analyze the company "%s" as a business analyst.
                %s
                Include exactly 3 key competitors, 3 products, 3 strengths, and 3 weaknesses.
                Keep every field brief, factual, and concise.
                """.formatted(cleanCompany, groundingSection);

        Schema competitorItemSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "mainProduct", Schema.builder().type(Type.Known.STRING).build(),
                        "howItCompares", Schema.builder().type(Type.Known.STRING).build()
                ))
                .required(List.of("name", "mainProduct", "howItCompares"))
                .build();

        Schema reportSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "companyName", Schema.builder().type(Type.Known.STRING).build(),
                        "overview", Schema.builder().type(Type.Known.STRING).build(),
                        "products", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "competitors", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(competitorItemSchema)
                                .build(),
                        "strengths", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "weaknesses", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build()
                ))
                .required(List.of("companyName", "overview", "products", "competitors", "strengths", "weaknesses"))
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(reportSchema)
                .temperature(0.2F)
                .maxOutputTokens(4096)
                .build();

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(MODEL_ID, prompt, config);
        } catch (ApiException e) {
            if (e.code() == 429) {
                response = client.models.generateContent("gemini-3.5-flash-lite", prompt, config);
            } else {
                throw e;
            }
        }

        if (response.finishReason().isPresent()
                && response.finishReason().get().knownEnum() == FinishReason.Known.MAX_TOKENS) {
            throw new IllegalStateException("AI response exceeded token limit before finishing JSON.");
        }

        String jsonText = cleanJson(response.text());
        CompetitorReport rawReport = objectMapper.readValue(jsonText, CompetitorReport.class);

        CompetitorReport finalReport = new CompetitorReport(
                rawReport.companyName() != null && !rawReport.companyName().isBlank()
                        ? rawReport.companyName()
                        : cleanCompany,
                sourceNote,
                rawReport.overview(),
                rawReport.products(),
                rawReport.competitors(),
                rawReport.strengths(),
                rawReport.weaknesses(),
                searchContext.sources()
        );

        reportCache.put(cacheKey, finalReport);
        return finalReport;
    }

    private String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response.");
        }
        String trimmed = raw.trim();
        if (trimmed.
