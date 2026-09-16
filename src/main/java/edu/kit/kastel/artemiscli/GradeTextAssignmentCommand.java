package edu.kit.kastel.artemiscli;

import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.TextExercise;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "grade-text", mixinStandardHelpOptions = true,
    description = "Grades a text assignment.")
public class GradeTextAssignmentCommand implements Command {
    private static final int NUMBER_OF_TRIES = 20;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec specification;

    @CommandLine.ParentCommand
    private Main parent;

    @CommandLine.Option(names = {"--exercise"}, description = "Will grade the given exercise.", required = true)
    private String exerciseName;

    @CommandLine.Option(names = {"--points"}, description = "The points to give for a valid submission.")
    private Double points;

    @CommandLine.Option(names = {"--tries"}, description = "The total amount of submissions it should try to assess.")
    private Integer tries;

    @CommandLine.Option(names = {"--command"}, description = "Executes the given command to evaluate a submission. The script will receive the submission text as the first argument and ")
    private String command;

    @CommandLine.Parameters(index = "0", arity = "0..*", description = "Regular expressions that define valid submissions. Note that invalid submissions will be ignored.")
    private String[] validRegexes;

    private record CommandTextAssessment(double points, String text) {
        private static Optional<CommandTextAssessment> parse(String output) {
            String[] parts = output.split(" - ", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }

            double points = Double.parseDouble(parts[0].trim());
            String text = parts[1].trim();

            return Optional.of(new CommandTextAssessment(points, text));
        }
    }

    private int numberOfTries() {
        return this.tries != null ? this.tries : NUMBER_OF_TRIES;
    }

    private Optional<CommandTextAssessment> validate(String rawText) throws Exception {
        String text = rawText == null ? "" : rawText.trim();

        if (this.validRegexes != null && Arrays.stream(this.validRegexes).anyMatch(text::matches)) {
            return Optional.of(new CommandTextAssessment(this.points, "Super gemacht :)"));
        }

        if (this.command == null) {
            return Optional.empty();
        }

        ProcessBuilder processBuilder = new ProcessBuilder(this.command.split(" "));
        //processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // When this try block is closed, it will send an EOF, indicating that there will be no more to read.
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(text);
            writer.flush();
        }

        // This should prevent this tool from freezing:
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            System.err.println("Warning: Evaluation command timed out.");
            return Optional.empty();
        }

        if (process.exitValue() != 0) {
            throw new IllegalStateException("Evaluation command failed with exit code %d%nstdout: %s%nstderr: %s".formatted(
                + process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
            ));
        }

        // Pass along the stdout of the program:
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!output.isBlank()) {
            System.out.println(output);
        }

        // The result of the program will be on the error stream, to keep them separate from debug messages to stdout:
        return CommandTextAssessment.parse(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Override
    public void execute() throws Exception {
        TextExercise exercise = findExercise(this.parent.course(), this.exerciseName);

        if (this.validRegexes == null && this.command == null) {
            System.out.println("Warning: No regex or command for valid submissions provided, all submissions will be ignored.");
            return;
        }

        if (this.validRegexes != null && this.points == null) {
            throw new IllegalStateException("points are required when regex are specified.");
        }

        Collection<String> seenSubmissions = new HashSet<>();

        // the code doesn't use the listSubmissions endpoint, because the locking from there is not allowed for tutors
        // for text exercises specifically (for programming submissions that works)
        for (int i = 0; i < this.numberOfTries(); i++) {
            var assessment = exercise.tryLockNextSubmission(0).orElse(null);
            if (assessment == null) {
                break;
            }

            String text = assessment.getText().trim();

            boolean isNewSubmission = seenSubmissions.add(text);
            boolean hasSubmitted = false;
            try {
                Optional<CommandTextAssessment> result = this.validate(text);

                if (result.isPresent()) {
                    System.out.println("Found a valid submission: '%s'".formatted(text));
                    assessment.addAnnotation(result.get().points(), result.get().text(), 0, text.length());
                    assessment.submit();
                    hasSubmitted = true;
                }

                if (result.isEmpty() && isNewSubmission) {
                    System.out.println("The submission is not yet validated by the command: " + text);
                }
            } finally {
                if (!hasSubmitted) {
                    assessment.cancel();
                }
            }
        }
    }

    private static TextExercise findExercise(Course course, String name) throws ArtemisNetworkException {
        if (name == null) {
            return null;
        }

        List<String> availableExercises = new ArrayList<>();

        for (var exercise : course.getTextExercises()) {
            if (name.equalsIgnoreCase(exercise.getShortName()) || name.equalsIgnoreCase(exercise.getTitle()) || name.equalsIgnoreCase("" + exercise.getId())) {
                return exercise;
            }

            if (exercise.getShortName() != null) {
                availableExercises.add(exercise.getShortName());
            } else if (exercise.getTitle() != null) {
                availableExercises.add(exercise.getTitle());
            } else {
                availableExercises.add("" + exercise.getId());
            }
        }

        throw new IllegalStateException("Failed to find exercise with name \"%s\", available: %s".formatted(name, availableExercises));
    }
}
