package competitor_research;

public class Competitor {

    private String name;
    private String industry;
    private String website;

    public Competitor(String name, String industry, String website) {
        this.name = name;
        this.industry = industry;
        this.website = website;
    }

    public String getName() {
        return name;
    }

    public String getIndustry() {
        return industry;
    }

    public String getWebsite() {
        return website;
    }
}