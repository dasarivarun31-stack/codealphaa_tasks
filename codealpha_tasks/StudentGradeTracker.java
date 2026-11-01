package codealpha_tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class StudentGradeTracker {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Student> students = new ArrayList<>();
        boolean running = true;

        System.out.println("Student Grade Tracker");
        while (running) {
            System.out.println("\n1. Add student grades");
            System.out.println("2. Display summary report");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    addStudent(scanner, students);
                    break;
                case "2":
                    displaySummary(students);
                    break;
                case "3":
                    running = false;
                    System.out.println("Goodbye.");
                    break;
                default:
                    System.out.println("Please choose 1, 2, or 3.");
            }
        }

        scanner.close();
    }

    private static void addStudent(Scanner scanner, List<Student> students) {
        System.out.print("Student name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Name cannot be blank.");
            return;
        }

        List<Double> scores = new ArrayList<>();
        while (true) {
            System.out.print("Enter a score from 0 to 100, or 'done' to finish: ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("done")) {
                if (scores.isEmpty()) {
                    System.out.println("Enter at least one score before finishing.");
                    continue;
                }
                break;
            }

            try {
                double score = Double.parseDouble(input);
                if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 100) {
                    System.out.println("Score must be between 0 and 100.");
                } else {
                    scores.add(score);
                }
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid number or 'done'.");
            }
        }

        students.add(new Student(name, scores));
        System.out.println("Grades saved for " + name + ".");
    }

    private static void displaySummary(List<Student> students) {
        if (students.isEmpty()) {
            System.out.println("No student grades have been entered yet.");
            return;
        }

        double total = 0;
        double overallHighest = Double.NEGATIVE_INFINITY;
        double overallLowest = Double.POSITIVE_INFINITY;
        int scoreCount = 0;

        System.out.println("\nStudent Summary");
        System.out.printf(Locale.ROOT, "%-20s %-28s %8s %8s %8s%n",
                "Student", "Scores", "Average", "Highest", "Lowest");
        System.out.println("------------------------------------------------------------------------");

        for (Student student : students) {
            double studentTotal = 0;
            double highest = Double.NEGATIVE_INFINITY;
            double lowest = Double.POSITIVE_INFINITY;

            for (double score : student.scores) {
                studentTotal += score;
                highest = Math.max(highest, score);
                lowest = Math.min(lowest, score);
                total += score;
                overallHighest = Math.max(overallHighest, score);
                overallLowest = Math.min(overallLowest, score);
                scoreCount++;
            }

            double average = studentTotal / student.scores.size();
            System.out.printf(Locale.ROOT, "%-20s %-28s %8.2f %8.2f %8.2f%n",
                    student.name, formatScores(student.scores), average, highest, lowest);
        }

        System.out.println("------------------------------------------------------------------------");
        System.out.printf(Locale.ROOT, "Class average: %.2f | Highest score: %.2f | Lowest score: %.2f%n",
                total / scoreCount, overallHighest, overallLowest);
    }

    private static String formatScores(List<Double> scores) {
        StringBuilder result = new StringBuilder();
        for (double score : scores) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(String.format(Locale.ROOT, "%.2f", score));
        }
        return result.toString();
    }

    private static final class Student {
        private final String name;
        private final List<Double> scores;

        private Student(String name, List<Double> scores) {
            this.name = name;
            this.scores = scores;
        }
    }
}