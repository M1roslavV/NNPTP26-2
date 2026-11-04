package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.domain.exception.TaskPulseException;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskQuery;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.service.TaskSortOrder;

import java.io.PrintStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;

public class TaskPulseCli {
    private final ProjectService projectService;
    private final TaskService taskService;
    private final PrintStream out;
    private final PrintStream err;
    private final ReportCliHandler reportHandler;

    public TaskPulseCli(ProjectService projectService, TaskService taskService, PrintStream out, PrintStream err) {
        this(projectService, taskService, out, err, Clock.systemDefaultZone());
    }

    public TaskPulseCli(ProjectService projectService, TaskService taskService, PrintStream out, PrintStream err,
                        Clock clock) {
        this.projectService = Objects.requireNonNull(projectService, "ProjectService must not be null");
        this.taskService = Objects.requireNonNull(taskService, "TaskService must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
        this.reportHandler = new ReportCliHandler(projectService, taskService, out, err,
                Objects.requireNonNull(clock, "Clock must not be null"));
    }

    public TaskPulseCli(ProjectService projectService, TaskService taskService) {
        this(projectService, taskService, System.out, System.err);
    }

    public void runInteractive(Scanner scanner) {
        out.println("TaskPulse CLI — type 'help' for commands, 'exit' to quit.");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            boolean keepRunning = executeLine(line);
            if (!keepRunning) {
                break;
            }
        }
    }

    public boolean executeLine(String line) {
        if (line == null) {
            return false;
        }
        line = line.strip();
        if (line.isEmpty()) {
            return true;
        }

        List<String> tokens = CommandTokenizer.tokenize(line);
        if (tokens.isEmpty()) {
            return true;
        }

        String command = tokens.get(0).toLowerCase();
        if (command.equals("exit") || command.equals("quit")) {
            return false;
        }

        try {
            switch (command) {
                case "help" -> printHelp();
                case "project" -> handleProjectCommand(tokens.subList(1, tokens.size()));
                case "task" -> handleTaskCommand(tokens.subList(1, tokens.size()));
                case "worklog" -> handleWorklogCommand(tokens.subList(1, tokens.size()));
                case "report" -> reportHandler.execute(tokens.subList(1, tokens.size()));
                default -> err.println("Unknown command: '" + command + "'. Type 'help' for available commands.");
            }
        } catch (TaskPulseException e) {
            err.println("Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            err.println("Invalid argument: " + e.getMessage());
        } catch (Exception e) {
            err.println("Unexpected error: " + e.getMessage());
        }
        return true;
    }

    private void printHelp() {
        out.println("Available commands:");
        out.println("  project add <name> [description]");
        out.println("  project list");
        out.println("  project show <project-id>");
        out.println("  project edit <project-id> <name> [description]");
        out.println("  project delete <project-id>");
        out.println("  task add <project-id> <title> [description]");
        out.println("  task list [--project <id>] [--state <state>] [--priority <priority>] [--label <label>]"
                + " [--search <text>] [--exceeded [<minutes>]] [--sort <title|priority|duedate|priority-duedate>]");
        out.println("  task show <task-id>");
        out.println("  task transition <task-id> <state>");
        out.println("  task label add <task-id> <label>");
        out.println("  task label remove <task-id> <label>");
        out.println("  task delete <task-id>");
        out.println("  worklog log <task-id> <durationMinutes> --author <author> [--desc <description>]");
        out.println("  worklog list <task-id>");
        out.println("  " + ReportCliHandler.USAGE);
        out.println("    Without --output, prints to the console. Existing files are never overwritten.");
        out.println("  exit / quit");
    }

    private void handleProjectCommand(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: project <add|list|show|edit|delete> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase();
        switch (action) {
            case "add" -> {
                if (args.size() < 2) {
                    err.println("Usage: project add <name> [description]");
                    return;
                }
                String name = args.get(1);
                String desc = args.size() > 2 ? args.get(2) : "";
                Project project = projectService.createProject(name, desc);
                out.println("Project created: " + project.getId() + " - " + project.getName());
            }
            case "list" -> {
                List<Project> projects = projectService.listProjects();
                if (projects.isEmpty()) {
                    out.println("No projects found.");
                } else {
                    for (Project p : projects) {
                        out.println(p.getId() + " | " + p.getName() + (p.getDescription().isEmpty() ? "" : " | " + p.getDescription()));
                    }
                }
            }
            case "show" -> {
                if (args.size() < 2) {
                    err.println("Usage: project show <project-id>");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                Project p = projectService.getProject(id);
                out.println("ID:          " + p.getId());
                out.println("Name:        " + p.getName());
                out.println("Description: " + p.getDescription());
            }
            case "edit" -> {
                if (args.size() < 3) {
                    err.println("Usage: project edit <project-id> <name> [description]");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                String name = args.get(2);
                String desc = args.size() > 3 ? args.get(3) : "";
                Project p = projectService.updateProject(id, name, desc);
                out.println("Project updated: " + p.getId() + " - " + p.getName());
            }
            case "delete" -> {
                if (args.size() < 2) {
                    err.println("Usage: project delete <project-id>");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                projectService.deleteProject(id);
                out.println("Project deleted: " + id);
            }
            default -> err.println("Unknown project action: '" + action + "'");
        }
    }

    private void handleTaskCommand(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: task <add|list|show|transition|label|delete> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase();
        switch (action) {
            case "add" -> {
                if (args.size() < 3) {
                    err.println("Usage: task add <project-id> <title> [description]");
                    return;
                }
                UUID projectId = parseUuid(args.get(1));
                String title = args.get(2);
                String desc = args.size() > 3 ? args.get(3) : "";
                Task task = taskService.createTask(projectId, title, desc, Priority.MEDIUM, null, null);
                out.println("Task created: " + task.getId() + " - " + task.getTitle());
            }
            case "list" -> {
                TaskQuery.Builder qb = TaskQuery.builder();
                for (int i = 1; i < args.size(); i++) {
                    String flag = args.get(i);
                    if ("--project".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.projectId(parseUuid(args.get(++i)));
                    } else if ("--state".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.state(parseState(args.get(++i)));
                    } else if ("--priority".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.priority(parsePriority(args.get(++i)));
                    } else if ("--label".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.label(args.get(++i));
                    } else if ("--search".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.textSearch(args.get(++i));
                    } else if ("--exceeded".equalsIgnoreCase(flag)) {
                        long threshold = 0;
                        if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                            threshold = parseVarianceThreshold(args.get(++i));
                        }
                        qb.varianceThresholdMinutes(threshold);
                    } else if ("--sort".equalsIgnoreCase(flag) && i + 1 < args.size()) {
                        qb.sortOrder(parseSortOrder(args.get(++i)));
                    }
                }
                TaskQuery query = qb.build();
                List<Task> tasks = taskService.findTasks(query);
                if (tasks.isEmpty()) {
                    out.println("No tasks found.");
                } else {
                    for (Task t : tasks) {
                        String alert = query.varianceThresholdMinutes() == null ? ""
                                : " | " + t.getVarianceMinutes() + " min over estimate";
                        out.println(t.getId() + " | " + t.getState() + " | " + t.getPriority() + " | " + t.getTitle() + alert);
                    }
                }
            }
            case "show" -> {
                if (args.size() < 2) {
                    err.println("Usage: task show <task-id>");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                Task t = taskService.getTask(id);
                out.println("ID:          " + t.getId());
                out.println("Project:     " + t.getProjectId());
                out.println("Title:       " + t.getTitle());
                out.println("Description: " + t.getDescription());
                out.println("State:       " + t.getState());
                out.println("Priority:    " + t.getPriority());
                out.println("Estimate:    " + (t.getEstimatedMinutes() == null ? "not estimated" : t.getEstimatedMinutes() + " min"));
                out.println("Due date:    " + (t.getDueDate() == null ? "none" : t.getDueDate()));
                out.println("Labels:      " + (t.getLabels().isEmpty() ? "none" : String.join(", ", t.getLabels())));
                out.println("Worklogs:    " + t.getWorklogs().size() + " (" + t.getTotalRecordedMinutes() + " min recorded)");
                Long variance = t.getVarianceMinutes();
                out.println("Variance:    " + (variance == null ? "unavailable" : variance + " min"));
            }
            case "transition" -> {
                if (args.size() < 3) {
                    err.println("Usage: task transition <task-id> <state>");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                TaskState nextState = parseState(args.get(2));
                Task t = taskService.transitionTask(id, nextState);
                out.println("Task " + t.getId() + " transitioned to " + t.getState());
            }
            case "label" -> {
                if (args.size() < 4) {
                    err.println("Usage: task label <add|remove> <task-id> <label>");
                    return;
                }
                String sub = args.get(1).toLowerCase();
                UUID id = parseUuid(args.get(2));
                String label = args.get(3);
                if ("add".equals(sub)) {
                    taskService.addLabel(id, label);
                    out.println("Label '" + label + "' added to task " + id);
                } else if ("remove".equals(sub)) {
                    taskService.removeLabel(id, label);
                    out.println("Label '" + label + "' removed from task " + id);
                } else {
                    err.println("Unknown label action: " + sub);
                }
            }
            case "delete" -> {
                if (args.size() < 2) {
                    err.println("Usage: task delete <task-id>");
                    return;
                }
                UUID id = parseUuid(args.get(1));
                taskService.deleteTask(id);
                out.println("Task deleted: " + id);
            }
            default -> err.println("Unknown task action: '" + action + "'");
        }
    }

    private void handleWorklogCommand(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: worklog <log|list> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase();
        switch (action) {
            case "log" -> {
                if (args.size() < 3) {
                    err.println("Usage: worklog log <task-id> <durationMinutes> --author <author> [--desc <description>]");
                    return;
                }
                UUID taskId = parseUuid(args.get(1));
                long duration;
                try {
                    duration = Long.parseLong(args.get(2));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Duration must be a valid integer number of minutes");
                }
                String author = null;
                String desc = "";
                for (int i = 3; i < args.size(); i++) {
                    if ("--author".equalsIgnoreCase(args.get(i)) && i + 1 < args.size()) {
                        author = args.get(++i);
                    } else if ("--desc".equalsIgnoreCase(args.get(i)) && i + 1 < args.size()) {
                        desc = args.get(++i);
                    }
                }
                if (author == null) {
                    err.println("Missing required --author argument");
                    return;
                }
                Worklog worklog = taskService.logWork(taskId, duration, author, desc);
                out.println("Work logged: " + worklog.durationMinutes() + " min by " + worklog.author() + " on task " + taskId);
            }
            case "list" -> {
                if (args.size() < 2) {
                    err.println("Usage: worklog list <task-id>");
                    return;
                }
                UUID taskId = parseUuid(args.get(1));
                Task task = taskService.getTask(taskId);
                List<Worklog> worklogs = task.getWorklogs();
                if (worklogs.isEmpty()) {
                    out.println("No worklogs for task " + taskId);
                } else {
                    for (Worklog w : worklogs) {
                        out.println(w.timestamp() + " | " + w.durationMinutes() + " min | " + w.author() + (w.description().isEmpty() ? "" : " | " + w.description()));
                    }
                }
            }
            default -> err.println("Unknown worklog action: '" + action + "'");
        }
    }

    private long parseVarianceThreshold(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--exceeded expects a whole number of minutes, got '" + value + "'");
        }
    }

    private UUID parseUuid(String val) {
        try {
            return UUID.fromString(val.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: '" + val + "'");
        }
    }

    private TaskState parseState(String val) {
        try {
            return TaskState.valueOf(val.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid task state: '" + val + "'. Allowed: " + Arrays.toString(TaskState.values()));
        }
    }

    private Priority parsePriority(String val) {
        try {
            return Priority.valueOf(val.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: '" + val + "'. Allowed: " + Arrays.toString(Priority.values()));
        }
    }

    private TaskSortOrder parseSortOrder(String val) {
        return switch (val.strip().toLowerCase()) {
            case "title" -> TaskSortOrder.TITLE_ASC;
            case "priority" -> TaskSortOrder.PRIORITY_DESC;
            case "duedate" -> TaskSortOrder.DUE_DATE_ASC;
            case "priority-duedate" -> TaskSortOrder.PRIORITY_DESC_THEN_DUE_DATE_ASC;
            default -> throw new IllegalArgumentException("Invalid sort order: '" + val + "'. Allowed: [title, priority, duedate, priority-duedate]");
        };
    }
}
