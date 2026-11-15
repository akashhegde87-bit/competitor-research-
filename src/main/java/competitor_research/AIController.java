package competitor_research;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AIController {

    private final GeminiService geminiService;

    public AIController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/api/ai/research")
    public CompetitorReport research(
            @RequestParam String company,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return geminiService.researchCompany(company, refresh);
    }

    @GetMapping("/api/ai/history")
    public List<String> getHistory() {
        return geminiService.getRecentCompanies();
    }
}