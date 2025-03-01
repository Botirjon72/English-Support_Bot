package org.example;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeepgramResponse {
    private Results results;

    public Results getResults() { return results; }
    public void setResults(Results results) { this.results = results; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Results {
    private List<Channel> channels;

    public List<Channel> getChannels() { return channels; }
    public void setChannels(List<Channel> channels) { this.channels = channels; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Channel {
    private List<Alternative> alternatives;

    public List<Alternative> getAlternatives() { return alternatives; }
    public void setAlternatives(List<Alternative> alternatives) { this.alternatives = alternatives; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Alternative {
    private String transcript;

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
}
