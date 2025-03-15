package org.example;

public class GrammarRule {
    private int id;
    private String ruleName;
    private String description;
    private String negativeExample;
    private String questionExample;
    private String youtubeLink;

    public GrammarRule(int id, String ruleName, String description, String negativeExample, String questionExample, String youtubeLink) {
        this.id = id;
        this.ruleName = ruleName;
        this.description = description;
        this.negativeExample = negativeExample;
        this.questionExample = questionExample;
        this.youtubeLink = youtubeLink;
    }

    public int getId() {
        return id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getDescription() {
        return description;
    }

    public String getNegativeExample() {
        return negativeExample;
    }

    public String getQuestionExample() {
        return questionExample;
    }

    public String getYoutubeLink() {
        return youtubeLink;
    }
}
