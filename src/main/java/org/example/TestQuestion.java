package org.example;

public class TestQuestion {
    private int id;
    private String level;
    private String question;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private char correctAnswer;

    public TestQuestion(int id, String level, String question, String optionA, String optionB,
                        String optionC, String optionD, char correctAnswer) {
        this.id = id;
        this.level = level;
        this.question = question;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public char getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(char correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    @Override
    public String toString() {
        return "Level: " + level + "\n" +
                "Q: " + question + "\n" +
                "A) " + optionA + "\n" +
                "B) " + optionB + "\n" +
                "C) " + optionC + "\n" +
                "D) " + optionD + "\n" +
                "Correct: " + correctAnswer;
    }
}