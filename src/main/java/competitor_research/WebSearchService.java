package competitor_research;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public record SearchContext(
            String formattedSnippets,
            List<WebSource> sources,
            boolean liveGrounded
    ) {
    }

    public WebSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.apiKey = System.getenv("TAVILY_API_KEY");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.tavily.com")
                .requestFactory(factory)
                .build();
    }

    public SearchContext searchCompany(String company) {
        if (apiKey == null || apiKey.isBlank()) {
            return new SearchContext("", List.of(), false);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", company + " products competitors overview",
                    "search_depth", "basic",
                    "max_results", 3
            );

            String rawJson = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                return new SearchContext("", List.of(), false);
            }

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode resultsNode = root.path("results");

            if (!resultsNode.isArray() || resultsNode.isEmpty()) {
                return new SearchContext("", List.of(), false);
            }

            List<WebSource> sources = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            int index = 1;

            for (JsonNode item : resultsNode) {
                String title = item.path("title").asText("Untitled Source");
                String url = item.path("url").asText("");
                String content = item.path("content").asText("");

                if (content.length() > 400) {
                    content = content.substring(0, 400) + "...";
                }

                if (!url.isBlank()) {
                    sources.add(new WebSource(title, url));
                }
                contextBuilder.append("Source ").append(index)
                        .append(" (").append(title).append("): ")
                        .append(content).append("\n");
                index++;
            }

            return new SearchContext(contextBuilder.toString(), sources, !sources.isEmpty());
        } catch (Exception ex) {
            System.err.println("Live search skipped (timeout/unavailable): " + ex.getMessage());
            return new SearchContext("", List.of(), false);
        }
    }
}