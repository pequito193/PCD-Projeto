package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.QuizFile;
import model.Quiz;
import model.Question;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;

public class JsonLoader {
    private QuizFile quizFile;

    public JsonLoader(String path) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Reader r = new FileReader(path)) {
            quizFile = gson.fromJson(r, QuizFile.class);
        }
    }

    public List<Quiz> getQuizzes() {
        return quizFile.getQuizzes();
    }
}
