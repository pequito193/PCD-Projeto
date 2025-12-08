package model;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private String question;
    private int points;
    private int correct; // 1-based index of correct option
    private List<String> options;

    public Question(String question, int points, int correct, List<String> options) {
        this.question = question;
        this.points = points;
        this.correct = correct;
        this.options = options;
    }

    public String getQuestion() {
        return question;
    }

    public int getPoints() {
        return points;
    }

    public int getCorrect() {
        return correct;
    }

    public List<String> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "Question{" + "question='" + question + '\'' + ", points=" + points + ", correct=" + correct + ", options=" + options + '}';
    }

    
}
