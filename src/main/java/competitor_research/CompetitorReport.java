package competitor_research;

import java.util.List;

public record CompetitorReport(
        String companyName,
        String sourceNote,
        String overview,
        List<String> products,
        List<CompetitorInfo> competitors,
        List<String> strengths,
        List<String> weaknesses,
        List<WebSource> sources
) {
}