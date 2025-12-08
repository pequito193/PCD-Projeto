package model;

import java.io.Serializable;
import java.util.List;

public class Quiz implements Serializable {
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
