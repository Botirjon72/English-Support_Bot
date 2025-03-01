package org.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true) // Boshqa maydonlar bo'lsa e'tiborga olinmaydi
public class HuggingFaceResponse {
    @JsonProperty("generated_text") // API JSON maydonini Java ga moslaymiz
    private String generatedText;

    public String getGeneratedText() {
        return generatedText;
    }

    public void setGeneratedText(String generatedText) {
        this.generatedText = generatedText;
    }
}
