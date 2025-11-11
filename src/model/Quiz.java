package model;

import java.util.List;

public class Quiz {
    private String name;
    private List<Question> questions;

    public Quiz() {}

    public String getName() {
        return name;
    }

    public List<Question> getQuestions() {
        return questions;
    }
}
