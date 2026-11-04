package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.report.MarkdownReportExporter;
import cz.upce.fei.nnptp.taskpulse.report.ReportExporter;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskQuery;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ReportCliHandler {
    static final String USAGE = "report export <project-id> --format markdown [--output <file-path>]";

    private final ProjectService projects;
    private final TaskService tasks;
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;

    ReportCliHandler(ProjectService projects, TaskService tasks, PrintStream out, PrintStream err, Clock clock) {
        this.projects = projects;
        this.tasks = tasks;
        this.out = out;
        this.err = err;
        this.clock = clock;
    }

    void execute(List<String> args) {
        if (args.size() < 2 || !"export".equalsIgnoreCase(args.get(0))) {
            throw new IllegalArgumentException("Usage: " + USAGE);
        }
        UUID projectId = UUID.fromString(args.get(1));
        String format = null;
        Path output = null;
        for (int i = 2; i < args.size(); i++) {
            String option = args.get(i);
            if (!"--format".equalsIgnoreCase(option) && !"--output".equalsIgnoreCase(option)) {
                throw new IllegalArgumentException("Unknown report option: " + option + ". Usage: " + USAGE);
            }
            if (i + 1 >= args.size() || args.get(i + 1).startsWith("--") || args.get(i + 1).isBlank()) {
                throw new IllegalArgumentException("Missing value for " + option + ". Usage: " + USAGE);
            }
            String value = args.get(++i);
            if ("--format".equalsIgnoreCase(option)) {
                if (format != null) {
                    throw new IllegalArgumentException("Duplicate option: --format");
                }
                format = value;
            } else {
                if (output != null) {
                    throw new IllegalArgumentException("Duplicate option: --output");
                }
                output = Path.of(value);
            }
        }
        if (format == null) {
            throw new IllegalArgumentException("Missing --format. Usage: " + USAGE);
        }
        if (!"markdown".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("Unsupported report format: " + format + ". Supported: markdown");
        }

        Project project = projects.getProject(projectId);
        List<Task> projectTasks = tasks.findTasks(TaskQuery.builder().projectId(projectId).build());
        ReportExporter exporter = new MarkdownReportExporter();
        String report = exporter.export(project, projectTasks, LocalDate.now(clock));
        if (output == null) {
            out.print(report);
            return;
        }
        try {
            Files.writeString(output, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            out.println("Report exported to: " + output);
        } catch (FileAlreadyExistsException e) {
            err.println("Error: Output already exists: " + output + ". Choose a new file path.");
        } catch (IOException e) {
            err.println("Error: Cannot write report to " + output + ": " + e.getMessage());
        }
    }
}
