package competitor_research;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
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

        String groundingSection = searchContext.liveGrounded()
                ? "Use these live web snippets as primary context:\n" + searchContext.formattedSnippets()
                : "Rely on general business knowledge.";

        String prompt = """
                Analyze the company "%s" as a business analyst.
                %s
                Return ONLY valid JSON matching this schema (no markdown):
                {
                  "companyName": "%s",
                  "sourceNote": "%s",
                  "overview": "2 concise sentences on what the company does and its market",
                  "products": ["Product 1", "Product 2", "Product 3"],
                  "competitors": [
                    {
                      "name": "Competitor Name",
                      "mainProduct": "Main competing product",
                      "howItCompares": "1 short sentence comparing them"
                    }
                  ],
                  "strengths": ["Short point 1", "Short point 2", "Short point 3"],
                  "weaknesses": ["Short point 1", "Short point 2", "Short point 3"]
                }
                Include exactly 3 key competitors. Keep every field brief and factual.
                """.formatted(cleanCompany, groundingSection, cleanCompany, sourceNote);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(0.2F)
                .maxOutputTokens(1000)
                .build();

        GenerateContentResponse response = client.models.generateContent(
                MODEL_ID,
                prompt,
                config
        );

        CompetitorReport rawReport = objectMapper.readValue(response.text(), CompetitorReport.class);

        CompetitorReport finalReport = new CompetitorReport(
                rawReport.companyName(),
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

    public List<String> getRecentCompanies() {
        synchronized (reportCache) {
            List<String> companies = new ArrayList<>();
            for (CompetitorReport report : reportCache.values()) {
                companies.add(report.companyName());
            }
            Collections.reverse(companies);
            return companies;
        }
    }
}