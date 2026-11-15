package competitor_research;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CompetitorController {

    @GetMapping("/api/competitors")
    public List<Competitor> getCompetitors(
            @RequestParam String industry) {

        List<Competitor> allCompetitors = List.of(
                new Competitor("Zoho", "Software", "https://www.zoho.com"),
                new Competitor("Freshworks", "Software", "https://www.freshworks.com"),
                new Competitor("HDFC Bank", "Banking", "https://www.hdfcbank.com"),
                new Competitor("ICICI Bank", "Banking", "https://www.icicibank.com"),
                new Competitor("Amazon", "Ecommerce", "https://www.amazon.in"),
                new Competitor("Flipkart", "Ecommerce", "https://www.flipkart.com")
        );

        return allCompetitors.stream()
                .filter(company -> company.getIndustry()
                        .equalsIgnoreCase(industry))
                .toList();
    }
}