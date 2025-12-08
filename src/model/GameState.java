package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GameState implements Serializable {
    private Quiz quiz;
    private int currentIndex;
    private List<Integer> teamScores;

    public GameState(Quiz quiz, int numTeams) {
        this.quiz = quiz;
        this.currentIndex = 0;
        this.teamScores = new ArrayList<>();

        // Dar reset aos pontos das equipas
        for (int i = 0; i < numTeams; i++) {
            teamScores.add(0);
        }
    }

    public Question getCurrentQuestion() {
        if (quiz.getQuestions() == null || currentIndex >= quiz.getQuestions().size()) {
            return null;
        }

        return quiz.getQuestions().get(currentIndex);
    }

    public boolean nextQuestion() {
        if (currentIndex + 1 < quiz.getQuestions().size()) {
            currentIndex++;
            return true;
        }

        return false;
    }

    public void addPointsToTeam(int teamId, int points) {
        teamScores.set(teamId, teamScores.get(teamId) + points);
    }

    public List<Integer> getTeamScores() {
        return teamScores;
    }

    public int getCurrentIndex() {
        return currentIndex; }
    public int getTotalQuestions() {
        return quiz.getQuestions().size(); }
    public String getQuizName() {
        return quiz.getName(); }
    public Quiz getQuiz() {
        return quiz;}

}