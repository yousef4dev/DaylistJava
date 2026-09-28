package daylist;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DaylistApp {
    private static final Color INK = new Color(35, 43, 35);
    private static final Color MUTED = new Color(119, 126, 115);
    private static final Color GREEN = new Color(48, 87, 57);
    private static final Color PAPER = new Color(244, 245, 239);
    private static final Color LINE = new Color(228, 231, 222);
    private static final Color WHITE = new Color(255, 255, 251);
    private static final Color ORANGE = new Color(194, 99, 66);
    private static final Map<Color, Color> DARK_COLORS = Map.ofEntries(
            Map.entry(INK, new Color(216, 255, 222)),
            Map.entry(MUTED, new Color(112, 169, 121)),
            Map.entry(GREEN, new Color(57, 255, 20)),
            Map.entry(PAPER, new Color(7, 11, 8)),
            Map.entry(LINE, new Color(30, 59, 38)),
            Map.entry(WHITE, new Color(14, 22, 15)),
            Map.entry(ORANGE, new Color(141, 255, 102)),
            Map.entry(new Color(249, 250, 245), new Color(16, 26, 18)),
            Map.entry(new Color(233, 237, 227), new Color(25, 51, 30)),
            Map.entry(new Color(251, 252, 248), new Color(11, 18, 13)),
            Map.entry(new Color(190, 195, 186), new Color(70, 96, 74)),
            Map.entry(new Color(231, 239, 228), new Color(21, 60, 28)),
            Map.entry(new Color(36, 70, 48), new Color(8, 20, 10)),
            Map.entry(new Color(193, 211, 191), new Color(155, 234, 158)),
            Map.entry(new Color(174, 197, 174), new Color(112, 200, 124))
        );
    private static final List<String> PROJECTS = List.of("Inbox", "Work", "Personal", "Home", "Learning");
    private static final Path DATA_DIRECTORY = Path.of(System.getProperty("user.home"), ".local", "share", "daylist");
    private static final Path TASK_FILE = DATA_DIRECTORY.resolve("tasks.tsv");
    private static final Path FOCUS_FILE = DATA_DIRECTORY.resolve("focus.tsv");

    private final List<Task> tasks = new ArrayList<>();
    private final JFrame frame = new JFrame("Daylist");
    private final JPanel taskList = new JPanel();
    private final JPanel viewCards = new JPanel(new java.awt.CardLayout());
    private final JPanel calendarGrid = new JPanel(new GridLayout(0, 7, 4, 4));
    private final JPanel calendarTasks = new JPanel();
    private final JPanel scheduleList = new JPanel();
    private final JPanel taskListControls = new JPanel();
    private final JLabel viewHeading = new JLabel("Inbox");
    private final JLabel calendarMonthLabel = new JLabel();
    private final JLabel calendarSelectionLabel = new JLabel();
    private final JLabel taskCount = new JLabel();
    private final JLabel remainingLabel = new JLabel();
    private final JButton clearCompletedButton = textButton("Clear completed", ORANGE);
    private final JLabel doneTodayLabel = new JLabel("0");
    private final JLabel sessionsLabel = new JLabel("0");
    private final JLabel progressLabel = new JLabel("0 of 0");
    private final JPanel progressBar = new JPanel();
    private final JTextField searchField = new JTextField(16);
    private final JTextField taskInput = new JTextField();
    private final JComboBox<String> projectInput = new JComboBox<>(PROJECTS.toArray(String[]::new));
    private final JComboBox<String> priorityInput = new JComboBox<>(new String[]{"No priority", "High", "Medium", "Low"});
    private final JTextField dueInput = new JTextField(10);
    private final JComboBox<String> sortInput = new JComboBox<>(new String[]{"Recently added", "Due date", "Priority", "Alphabetical"});
    private final JLabel timerLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel timerModeLabel = new JLabel("Focus session", SwingConstants.CENTER);
    private final JButton timerToggle = new JButton("Start focus");
    private final JToggleButton darkModeToggle = new JToggleButton("Dark mode");
    private final JComboBox<String> durationInput = new JComboBox<>(new String[]{"15m", "25m", "50m"});
    private final Timer timer;
    private boolean darkMode = Preferences.userNodeForPackage(DaylistApp.class).getBoolean("darkMode", false);

    private String activeView = "Inbox";
    private String activeProject = "";
    private String activeFilter = "All";
    private long timerDeadline;
    private int remainingSeconds = 25 * 60;
    private int timerDurationSeconds = 25 * 60;
    private int focusSessions;
    private LocalDate focusDay = LocalDate.now();
    private LocalDate calendarMonth = LocalDate.now().withDayOfMonth(1);
    private LocalDate selectedCalendarDate = LocalDate.now();

    private DaylistApp() {
        loadTasks();
        loadFocusCount();
        timer = new Timer(250, event -> updateTimer());
        buildWindow();
        render();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setSystemLookAndFeel();
            new DaylistApp();
        });
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's cross-platform look and feel remains a usable fallback.
        }
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 650));
        frame.setSize(1220, 790);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(PAPER);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(WHITE);
        shell.add(buildSidebar(), BorderLayout.WEST);
        shell.add(buildWorkspace(), BorderLayout.CENTER);
        shell.add(buildFocusPanel(), BorderLayout.EAST);
        frame.setContentPane(shell);

        frame.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("control N"), "new-task");
        frame.getRootPane().getActionMap().put("new-task", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                taskInput.requestFocusInWindow();
            }
        });
        frame.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("control K"), "search");
        frame.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("meta K"), "search");
        frame.getRootPane().getActionMap().put("search", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
        frame.setVisible(true);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(205, 0));
        sidebar.setBackground(new Color(36, 70, 48));
        sidebar.setBorder(new EmptyBorder(22, 14, 18, 14));

        JLabel brand = new JLabel("  ✓   daylist");
        brand.setForeground(WHITE);
        brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        brand.setBorder(new EmptyBorder(0, 4, 22, 0));
        sidebar.add(brand);
        sidebar.add(sidebarSectionLabel("YOUR WORKSPACE"));
        addNavButton(sidebar, "Today", "Today");
        addNavButton(sidebar, "Upcoming", "Upcoming");
        addNavButton(sidebar, "Schedule", "Schedule");
        addNavButton(sidebar, "Calendar", "Calendar");
        addNavButton(sidebar, "Inbox", "Inbox");
        addNavButton(sidebar, "Completed", "Completed");
        sidebar.add(Box.createVerticalStrut(24));
        sidebar.add(sidebarSectionLabel("PROJECTS"));
        for (String project : PROJECTS.subList(1, PROJECTS.size())) {
            addNavButton(sidebar, "●   " + project, "project:" + project);
        }
        sidebar.add(Box.createVerticalGlue());

        darkModeToggle.setSelected(darkMode);
        darkModeToggle.setHorizontalAlignment(JButton.LEFT);
        darkModeToggle.setForeground(WHITE);
        darkModeToggle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        darkModeToggle.setFocusPainted(false);
        darkModeToggle.setBorder(new EmptyBorder(9, 10, 9, 8));
        darkModeToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        darkModeToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        darkModeToggle.setBackground(new Color(36, 70, 48));
        darkModeToggle.addActionListener(event -> {
            darkMode = darkModeToggle.isSelected();
            Preferences.userNodeForPackage(DaylistApp.class).putBoolean("darkMode", darkMode);
            render();
        });
        sidebar.add(darkModeToggle);
        sidebar.add(Box.createVerticalStrut(8));

        JLabel note = new JLabel("Small steps, done consistently.");
        note.setForeground(new Color(193, 211, 191));
        note.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        note.setBorder(new EmptyBorder(12, 5, 0, 5));
        sidebar.add(note);
        return sidebar;
    }

    private JLabel sidebarSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(174, 197, 174));
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setBorder(new EmptyBorder(4, 10, 9, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void addNavButton(JPanel sidebar, String label, String target) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setForeground(WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 10, 9, 8));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setBackground(new Color(36, 70, 48));
        button.addActionListener(event -> {
            if (target.startsWith("project:")) {
                activeProject = target.substring("project:".length());
                activeView = "";
            } else {
                activeProject = "";
                activeView = target;
            }
            activeFilter = "All";
            render();
        });
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(2));
    }

    private JPanel buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setBackground(WHITE);
        workspace.setBorder(new EmptyBorder(28, 30, 22, 30));

        JPanel top = new JPanel();
        top.setBackground(WHITE);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel headline = new JPanel(new BorderLayout());
        headline.setOpaque(false);
        JPanel headingText = new JPanel();
        headingText.setOpaque(false);
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("YOUR DAY, AT A GLANCE");
        eyebrow.setForeground(GREEN);
        eyebrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        viewHeading.setFont(new Font(Font.SERIF, Font.PLAIN, 32));
        viewHeading.setForeground(INK);
        JLabel date = new JLabel(LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
        date.setForeground(MUTED);
        date.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        headingText.add(eyebrow);
        headingText.add(Box.createVerticalStrut(7));
        headingText.add(viewHeading);
        headingText.add(Box.createVerticalStrut(4));
        headingText.add(date);
        headline.add(headingText, BorderLayout.WEST);
        top.add(headline);
        top.add(Box.createVerticalStrut(22));
        top.add(buildComposer());
        top.add(Box.createVerticalStrut(19));
        taskListControls.setOpaque(false);
        taskListControls.setLayout(new BoxLayout(taskListControls, BoxLayout.Y_AXIS));
        taskListControls.add(buildToolbar());
        taskListControls.add(Box.createVerticalStrut(14));
        taskListControls.add(buildListHeading());
        top.add(taskListControls);

        taskList.setLayout(new BoxLayout(taskList, BoxLayout.Y_AXIS));
        taskList.setBackground(WHITE);
        JScrollPane taskScroll = new JScrollPane(taskList);
        taskScroll.setBorder(BorderFactory.createLineBorder(LINE));
        taskScroll.getViewport().setBackground(WHITE);
        taskScroll.getVerticalScrollBar().setUnitIncrement(18);

        viewCards.setOpaque(false);
        viewCards.add(taskScroll, "tasks");
        viewCards.add(buildCalendarView(), "calendar");
        viewCards.add(buildScheduleView(), "schedule");

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        remainingLabel.setForeground(MUTED);
        remainingLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        clearCompletedButton.setVisible(false);
        clearCompletedButton.addActionListener(event -> clearCompleted());
        footer.add(remainingLabel, BorderLayout.WEST);
        footer.add(clearCompletedButton, BorderLayout.EAST);

        workspace.add(top, BorderLayout.NORTH);
        workspace.add(viewCards, BorderLayout.CENTER);
        workspace.add(footer, BorderLayout.SOUTH);
        return workspace;
    }

    private JPanel buildComposer() {
        JPanel composer = new JPanel(new BorderLayout(9, 8));
        composer.setBackground(WHITE);
        composer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(8, 10, 8, 10)));
        taskInput.setBorder(BorderFactory.createEmptyBorder(8, 3, 8, 3));
        taskInput.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        taskInput.setToolTipText("Add a task (press Enter)");
        taskInput.addActionListener(event -> addTask());
        JButton addButton = primaryButton("＋  Add task");
        addButton.addActionListener(event -> addTask());
        composer.add(taskInput, BorderLayout.CENTER);
        composer.add(addButton, BorderLayout.EAST);

        JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        details.setOpaque(false);
        styleCombo(projectInput);
        styleCombo(priorityInput);
        styleField(dueInput);
        dueInput.setText("");
        dueInput.setToolTipText("Optional due date, YYYY-MM-DD");
        details.add(projectInput);
        details.add(priorityInput);
        details.add(dueInput);
        composer.add(details, BorderLayout.SOUTH);
        return composer;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        taskCount.setForeground(MUTED);
        taskCount.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        searchField.setToolTipText("Search tasks (Ctrl+K)");
        searchField.setPreferredSize(new Dimension(180, 32));
        searchField.addActionListener(event -> render());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { render(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { render(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { render(); }
        });
        sortInput.addActionListener(event -> render());
        styleCombo(sortInput);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        tools.setOpaque(false);
        tools.add(searchField);
        tools.add(sortInput);
        toolbar.add(taskCount, BorderLayout.WEST);
        toolbar.add(tools, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildListHeading() {
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel label = new JLabel("YOUR TASKS");
        label.setForeground(INK);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String filter : List.of("All", "To do", "Done")) {
            javax.swing.JToggleButton button = new javax.swing.JToggleButton(filter);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(5, 9, 5, 9)));
            button.setSelected("All".equals(filter));
            button.setBackground(WHITE);
            group.add(button);
            button.addActionListener(event -> {
                activeFilter = filter;
                render();
            });
            filters.add(button);
        }
        heading.add(label, BorderLayout.WEST);
        heading.add(filters, BorderLayout.EAST);
        return heading;
    }

    private JPanel buildCalendarView() {
        JPanel calendar = new JPanel(new BorderLayout(0, 12));
        calendar.setBackground(WHITE);

        JPanel monthHeading = new JPanel(new BorderLayout());
        monthHeading.setOpaque(false);
        calendarMonthLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 20));
        calendarMonthLabel.setForeground(INK);
        JPanel monthActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        monthActions.setOpaque(false);
        JButton previous = textButton("‹", INK);
        previous.setToolTipText("Previous month");
        previous.addActionListener(event -> changeCalendarMonth(-1));
        JButton today = textButton("Today", GREEN);
        today.addActionListener(event -> {
            selectedCalendarDate = LocalDate.now();
            calendarMonth = selectedCalendarDate.withDayOfMonth(1);
            render();
        });
        JButton next = textButton("›", INK);
        next.setToolTipText("Next month");
        next.addActionListener(event -> changeCalendarMonth(1));
        monthActions.add(previous);
        monthActions.add(today);
        monthActions.add(next);
        monthHeading.add(calendarMonthLabel, BorderLayout.WEST);
        monthHeading.add(monthActions, BorderLayout.EAST);

        calendarGrid.setBackground(WHITE);
        JPanel monthPanel = new JPanel(new BorderLayout(0, 6));
        monthPanel.setOpaque(false);
        monthPanel.add(monthHeading, BorderLayout.NORTH);
        monthPanel.add(calendarGrid, BorderLayout.CENTER);

        JPanel detail = new JPanel(new BorderLayout(0, 6));
        detail.setOpaque(false);
        calendarSelectionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        calendarSelectionLabel.setForeground(INK);
        calendarTasks.setLayout(new BoxLayout(calendarTasks, BoxLayout.Y_AXIS));
        calendarTasks.setBackground(WHITE);
        JScrollPane taskScroll = new JScrollPane(calendarTasks);
        taskScroll.setBorder(BorderFactory.createLineBorder(LINE));
        taskScroll.getViewport().setBackground(WHITE);
        taskScroll.getVerticalScrollBar().setUnitIncrement(18);
        detail.add(calendarSelectionLabel, BorderLayout.NORTH);
        detail.add(taskScroll, BorderLayout.CENTER);

        calendar.add(monthPanel, BorderLayout.NORTH);
        calendar.add(detail, BorderLayout.CENTER);
        return calendar;
    }

    private JPanel buildScheduleView() {
        JPanel schedule = new JPanel(new BorderLayout(0, 10));
        schedule.setBackground(WHITE);
        JLabel heading = new JLabel("Upcoming tasks by due date");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        heading.setForeground(INK);
        scheduleList.setLayout(new BoxLayout(scheduleList, BoxLayout.Y_AXIS));
        scheduleList.setBackground(WHITE);
        JScrollPane scroll = new JScrollPane(scheduleList);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        schedule.add(heading, BorderLayout.NORTH);
        schedule.add(scroll, BorderLayout.CENTER);
        return schedule;
    }

    private void changeCalendarMonth(int amount) {
        calendarMonth = calendarMonth.plusMonths(amount);
        selectedCalendarDate = calendarMonth;
        render();
    }

    private JPanel buildFocusPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(250, 0));
        panel.setBackground(new Color(249, 250, 245));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, LINE), new EmptyBorder(28, 17, 20, 17)));

        JLabel title = new JLabel("FOCUS & PROGRESS");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        title.setForeground(INK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(15));

        JPanel timerCard = new JPanel();
        timerCard.setLayout(new BoxLayout(timerCard, BoxLayout.Y_AXIS));
        timerCard.setBackground(WHITE);
        timerCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(15, 13, 13, 13)));
        timerCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        timerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 235));
        JLabel small = new JLabel("A LITTLE FOCUSED TIME");
        small.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        small.setForeground(MUTED);
        small.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 43));
        timerLabel.setForeground(INK);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerModeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        timerModeLabel.setForeground(MUTED);
        timerModeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        timerToggle.addActionListener(event -> toggleTimer());
        JButton reset = textButton("Reset", MUTED);
        reset.setAlignmentX(Component.CENTER_ALIGNMENT);
        reset.addActionListener(event -> resetTimer());
        durationInput.setEditable(true);
        durationInput.setSelectedItem("25m");
        durationInput.setPreferredSize(new Dimension(120, 32));
        durationInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        durationInput.setToolTipText("Edit focus duration in minutes");
        durationInput.setAlignmentX(Component.CENTER_ALIGNMENT);
        durationInput.setBackground(WHITE);
        durationInput.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        durationInput.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GREEN), new EmptyBorder(4, 8, 4, 8)));
        durationInput.addActionListener(event -> setTimerDuration());
        timerCard.add(small);
        timerCard.add(Box.createVerticalStrut(9));
        timerCard.add(timerLabel);
        timerCard.add(timerModeLabel);
        timerCard.add(Box.createVerticalStrut(11));
        timerCard.add(timerToggle);
        timerCard.add(Box.createVerticalStrut(5));
        timerCard.add(reset);
        timerCard.add(Box.createVerticalStrut(8));
        timerCard.add(durationInput);
        panel.add(timerCard);
        panel.add(Box.createVerticalStrut(14));

        JPanel stats = new JPanel(new GridLayout(1, 2, 8, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        stats.add(statCard("DONE TODAY", doneTodayLabel));
        stats.add(statCard("FOCUS SESSIONS", sessionsLabel));
        panel.add(stats);
        panel.add(Box.createVerticalStrut(17));

        JPanel progress = new JPanel();
        progress.setLayout(new BoxLayout(progress, BoxLayout.Y_AXIS));
        progress.setOpaque(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        JPanel progressHeading = new JPanel(new BorderLayout());
        progressHeading.setOpaque(false);
        JLabel progressTitle = new JLabel("Daily progress");
        progressTitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        progressTitle.setForeground(MUTED);
        progressLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        progressLabel.setForeground(MUTED);
        progressHeading.add(progressTitle, BorderLayout.WEST);
        progressHeading.add(progressLabel, BorderLayout.EAST);
        progressBar.setBackground(new Color(233, 237, 227));
        progressBar.setPreferredSize(new Dimension(210, 6));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.add(progressHeading);
        progress.add(Box.createVerticalStrut(7));
        progress.add(progressBar);
        panel.add(progress);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel statCard(String title, JLabel value) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(10, 11, 10, 8)));
        value.setFont(new Font(Font.SERIF, Font.PLAIN, 24));
        value.setForeground(INK);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel caption = new JLabel(title);
        caption.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        caption.setForeground(MUTED);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(value);
        card.add(Box.createVerticalStrut(4));
        card.add(caption);
        return card;
    }

    private void addTask() {
        String text = taskInput.getText().trim();
        if (text.isEmpty()) return;
        LocalDate due = parseDueDate(dueInput.getText().trim());
        if (!dueInput.getText().isBlank() && due == null) {
            JOptionPane.showMessageDialog(frame, "Enter a due date as YYYY-MM-DD.", "Check the due date", JOptionPane.WARNING_MESSAGE);
            dueInput.requestFocusInWindow();
            return;
        }
        String project = (String) projectInput.getSelectedItem();
        String priority = (String) priorityInput.getSelectedItem();
        tasks.add(0, new Task(UUID.randomUUID().toString(), text, project, normalizePriority(priority), due, "", false, System.currentTimeMillis(), null));
        taskInput.setText("");
        dueInput.setText("");
        priorityInput.setSelectedIndex(0);
        activeView = "Inbox";
        activeProject = "";
        activeFilter = "All";
        saveTasks();
        render();
        taskInput.requestFocusInWindow();
    }

    private void render() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::render);
            return;
        }
        updateViewHeading();
        boolean listView = !"Calendar".equals(activeView) && !"Schedule".equals(activeView);
        taskListControls.setVisible(listView);
        taskList.removeAll();
        List<Task> visible = filteredTasks();
        for (Task task : visible) taskList.add(taskRow(task));
        if (visible.isEmpty()) taskList.add(emptyState());
        taskCount.setText(visible.size() + (visible.size() == 1 ? " task" : " tasks"));
        remainingLabel.setText(visible.stream().filter(task -> !task.done).count() + " remaining in this view");
        clearCompletedButton.setVisible(tasks.stream().anyMatch(task -> task.done && matchesView(task)));
        renderCalendar();
        renderSchedule();
        String card = "Calendar".equals(activeView) ? "calendar" : "Schedule".equals(activeView) ? "schedule" : "tasks";
        ((java.awt.CardLayout) viewCards.getLayout()).show(viewCards, card);
        updateProgress();
        taskList.revalidate();
        taskList.repaint();
        applyTheme(frame.getContentPane());
        frame.revalidate();
    }

    private void applyTheme(Component component) {
        Color background = component.getBackground();
        Color foreground = component.getForeground();
        if (component == viewHeading) {
            viewHeading.setFont(new Font(darkMode ? Font.MONOSPACED : Font.SERIF,
                darkMode ? Font.BOLD : Font.PLAIN, 32));
        } else if (component == timerLabel) {
            timerLabel.setFont(new Font(darkMode ? Font.MONOSPACED : Font.SERIF,
                darkMode ? Font.BOLD : Font.PLAIN, 43));
        }
        if (background != null) component.setBackground(themeColor(background));
        if (foreground != null) component.setForeground(themeColor(foreground));
        if (component instanceof JComponent swingComponent && swingComponent.getBorder() != null) {
            swingComponent.setBorder(themeBorder(swingComponent.getBorder()));
        }
        if (component instanceof JTextField || component instanceof JTextArea || component instanceof JComboBox<?>) {
            component.setBackground(darkMode ? new Color(29, 36, 30) : WHITE);
            component.setForeground(darkMode ? new Color(230, 235, 227) : INK);
        } else if (darkMode && component instanceof JButton button && !(component instanceof JToggleButton)) {
            component.setBackground(DARK_COLORS.get(WHITE));
            if (!DARK_COLORS.containsKey(foreground)) button.setForeground(new Color(230, 235, 227));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) applyTheme(child);
        }
    }

    private Color themeColor(Color color) {
        if (darkMode) return DARK_COLORS.getOrDefault(color, color);
        for (Map.Entry<Color, Color> entry : DARK_COLORS.entrySet()) {
            if (entry.getValue().equals(color)) return entry.getKey();
        }
        return color;
    }

    private Border themeBorder(Border border) {
        if (border instanceof CompoundBorder compound) {
            return new CompoundBorder(themeBorder(compound.getOutsideBorder()), themeBorder(compound.getInsideBorder()));
        }
        if (border instanceof MatteBorder matte && matte.getMatteColor() != null) {
            return new MatteBorder(matte.getBorderInsets(), themeColor(matte.getMatteColor()));
        }
        if (border instanceof LineBorder line) {
            return new LineBorder(themeColor(line.getLineColor()), line.getThickness(), line.getRoundedCorners());
        }
        return border;
    }

    private List<Task> filteredTasks() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Task> visible = new ArrayList<>();
        for (Task task : tasks) {
            if (!matchesView(task)) continue;
            if ("To do".equals(activeFilter) && task.done) continue;
            if ("Done".equals(activeFilter) && !task.done) continue;
            String searchable = (task.text + " " + task.notes + " " + task.project).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            visible.add(task);
        }
        Comparator<Task> comparator = switch ((String) sortInput.getSelectedItem()) {
            case "Due date" -> Comparator.comparing(task -> task.due == null ? LocalDate.MAX : task.due);
            case "Priority" -> Comparator.comparingInt(task -> priorityRank(task.priority));
            case "Alphabetical" -> Comparator.comparing(task -> task.text.toLowerCase(Locale.ROOT));
            default -> Comparator.comparingLong((Task task) -> task.createdAt).reversed();
        };
        visible.sort(comparator);
        return visible;
    }

    private void renderCalendar() {
        calendarMonthLabel.setText(calendarMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())));
        calendarSelectionLabel.setText(selectedCalendarDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())));
        calendarGrid.removeAll();
        for (String day : List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            label.setForeground(MUTED);
            calendarGrid.add(label);
        }
        LocalDate first = calendarMonth;
        LocalDate cellDate = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        for (int index = 0; index < 42; index++, cellDate = cellDate.plusDays(1)) {
            if (!cellDate.getMonth().equals(calendarMonth.getMonth())) {
                JLabel outsideDay = new JLabel(Integer.toString(cellDate.getDayOfMonth()), SwingConstants.CENTER);
                outsideDay.setForeground(new Color(190, 195, 186));
                calendarGrid.add(outsideDay);
                continue;
            }
            LocalDate date = cellDate;
            long dueCount = tasks.stream().filter(task -> date.equals(task.due) && !task.done).count();
                String dueColor = darkMode ? "#39ff14" : "#58745a";
            JButton dayButton = new JButton("<html><center>" + date.getDayOfMonth()
                    + (dueCount == 0 ? "" : "<br><font color='" + dueColor + "'>" + dueCount + " due</font>")
                    + "</center></html>");
            dayButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            dayButton.setForeground(INK);
            dayButton.setBackground(date.equals(selectedCalendarDate) ? new Color(231, 239, 228) : WHITE);
            dayButton.setOpaque(true);
            dayButton.setFocusPainted(false);
            dayButton.setBorder(BorderFactory.createLineBorder(date.equals(selectedCalendarDate) ? GREEN : LINE));
            dayButton.setPreferredSize(new Dimension(58, 48));
            dayButton.setToolTipText(dueCount + (dueCount == 1 ? " task due" : " tasks due"));
            dayButton.addActionListener(event -> {
                selectedCalendarDate = date;
                render();
            });
            calendarGrid.add(dayButton);
        }

        calendarTasks.removeAll();
        List<Task> selectedTasks = filteredTasks().stream()
                .filter(task -> selectedCalendarDate.equals(task.due)).toList();
        if (selectedTasks.isEmpty()) {
            JLabel empty = new JLabel("No tasks due on this date.");
            empty.setForeground(MUTED);
            empty.setBorder(new EmptyBorder(16, 12, 16, 12));
            calendarTasks.add(empty);
        } else {
            for (Task task : selectedTasks) calendarTasks.add(taskRow(task));
        }
        calendarGrid.revalidate();
        calendarGrid.repaint();
        calendarTasks.revalidate();
        calendarTasks.repaint();
    }

    private void renderSchedule() {
        scheduleList.removeAll();
        List<Task> scheduled = filteredTasks();
        scheduled.sort(Comparator.comparing((Task task) -> task.due)
                .thenComparing(task -> task.text.toLowerCase(Locale.ROOT)));
        if (scheduled.isEmpty()) {
            JLabel empty = new JLabel("No overdue or upcoming tasks with due dates.");
            empty.setForeground(MUTED);
            empty.setBorder(new EmptyBorder(16, 12, 16, 12));
            scheduleList.add(empty);
            return;
        }
        LocalDate headingDate = null;
        for (Task task : scheduled) {
            if (!task.due.equals(headingDate)) {
                headingDate = task.due;
                String label = headingDate.equals(LocalDate.now()) ? "Today"
                        : headingDate.equals(LocalDate.now().plusDays(1)) ? "Tomorrow"
                        : headingDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
                JLabel dayHeading = new JLabel(label);
                dayHeading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                dayHeading.setForeground(headingDate.isBefore(LocalDate.now()) ? ORANGE : GREEN);
                dayHeading.setBorder(new EmptyBorder(12, 12, 5, 12));
                scheduleList.add(dayHeading);
            }
            scheduleList.add(taskRow(task));
        }
        scheduleList.revalidate();
        scheduleList.repaint();
    }

    private boolean matchesView(Task task) {
        if (!activeProject.isEmpty()) return activeProject.equals(task.project);
        LocalDate today = LocalDate.now();
        return switch (activeView) {
            case "Today" -> task.due != null && !task.due.isAfter(today);
            case "Upcoming" -> task.due != null && task.due.isAfter(today);
            case "Schedule" -> task.due != null && (!task.done || !task.due.isBefore(today));
            case "Calendar" -> task.due != null && task.due.getYear() == calendarMonth.getYear()
                    && task.due.getMonth() == calendarMonth.getMonth();
            case "Completed" -> task.done;
            default -> "Inbox".equals(task.project);
        };
    }

    private void updateViewHeading() {
        String title = activeProject.isEmpty() ? activeView : activeProject;
        viewHeading.setText(switch (title) {
            case "Today" -> "A thoughtful day starts here.";
            case "Upcoming" -> "Keep an eye on what’s next.";
            case "Schedule" -> "Everything, in its own time.";
            case "Calendar" -> "See your days take shape.";
            case "Completed" -> "Look how far you’ve come.";
            case "Inbox" -> "Make room for what matters.";
            default -> "Your " + title.toLowerCase(Locale.ROOT) + " list.";
        });
    }

    private JPanel taskRow(Task task) {
        JPanel row = new JPanel(new BorderLayout(11, 0));
        row.setBackground(task.done ? new Color(251, 252, 248) : WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(11, 12, 11, 10)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

        JCheckBox check = new JCheckBox();
        check.setSelected(task.done);
        check.setBackground(row.getBackground());
        check.setFocusPainted(false);
        check.setToolTipText(task.done ? "Mark to do" : "Mark complete");
        check.addActionListener(event -> {
            task.done = check.isSelected();
            task.completedAt = task.done ? LocalDate.now() : null;
            saveTasks();
            render();
        });
        row.add(check, BorderLayout.WEST);

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel text = new JLabel();
        text.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        text.setForeground(task.done ? MUTED : INK);
        String escapedText = escapeHtml(task.text);
        text.setText(task.done ? "<html><strike>" + escapedText + "</strike></html>" : "<html>" + escapedText + "</html>");
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(text);

        List<String> metadata = new ArrayList<>();
        if (!"Inbox".equals(task.project)) metadata.add("● " + task.project);
        if (!"None".equals(task.priority)) metadata.add(task.priority + " priority");
        if (task.due != null) metadata.add((isOverdue(task) ? "Overdue · " : "Due · ") + task.due.format(DateTimeFormatter.ofPattern("MMM d")));
        if (!task.notes.isBlank()) metadata.add("Has notes");
        if (!metadata.isEmpty()) {
            JLabel meta = new JLabel(String.join("     ·     ", metadata));
            meta.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            meta.setForeground(isOverdue(task) ? ORANGE : MUTED);
            meta.setBorder(new EmptyBorder(5, 0, 0, 0));
            meta.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(meta);
        }
        row.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        actions.setOpaque(false);
        JButton edit = textButton("Edit", MUTED);
        edit.addActionListener(event -> editTask(task));
        JButton delete = textButton("×", ORANGE);
        delete.setToolTipText("Delete task");
        delete.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(frame, "Delete ‘" + task.text + "’?", "Delete task", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                tasks.remove(task);
                saveTasks();
                render();
            }
        });
        actions.add(edit);
        actions.add(delete);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private JPanel emptyState() {
        JPanel empty = new JPanel();
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        empty.setBackground(WHITE);
        empty.setBorder(new EmptyBorder(45, 16, 45, 16));
        String title;
        String caption;
        if (!searchField.getText().isBlank()) {
            title = "No matching tasks";
            caption = "Try another search or clear the search field.";
        } else if ("Completed".equals(activeView) || "Done".equals(activeFilter)) {
            title = "Nothing checked off yet";
            caption = "Completed tasks will find their way here.";
        } else if ("Today".equals(activeView)) {
            title = "Your day is open";
            caption = "Add a task and give it today’s date.";
        } else if ("Upcoming".equals(activeView)) {
            title = "Nothing on the horizon";
            caption = "Add a future due date to see it here.";
        } else if (!activeProject.isEmpty()) {
            title = "No tasks in " + activeProject;
            caption = "Tasks assigned to this project will appear here.";
        } else {
            title = "A little space to begin";
            caption = "Add a task above and take it from there.";
        }
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SERIF, Font.PLAIN, 20));
        heading.setForeground(INK);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel message = new JLabel(caption);
        message.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        message.setForeground(MUTED);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        empty.add(Box.createVerticalGlue());
        empty.add(heading);
        empty.add(Box.createVerticalStrut(7));
        empty.add(message);
        empty.add(Box.createVerticalGlue());
        return empty;
    }

    private void editTask(Task task) {
        JTextField text = new JTextField(task.text, 28);
        JComboBox<String> project = new JComboBox<>(PROJECTS.toArray(String[]::new));
        project.setSelectedItem(task.project);
        JComboBox<String> priority = new JComboBox<>(new String[]{"No priority", "High", "Medium", "Low"});
        priority.setSelectedItem(displayPriority(task.priority));
        JTextField due = new JTextField(task.due == null ? "" : task.due.toString(), 12);
        JTextArea notes = new JTextArea(task.notes, 5, 28);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel("Task"));
        form.add(text);
        form.add(new JLabel("Project"));
        form.add(project);
        form.add(new JLabel("Priority"));
        form.add(priority);
        form.add(new JLabel("Due date (YYYY-MM-DD)"));
        form.add(due);
        form.add(new JLabel("Notes"));
        form.add(new JScrollPane(notes));
        applyTheme(form);
        int result = JOptionPane.showConfirmDialog(frame, form, "Edit task", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        LocalDate parsedDue = parseDueDate(due.getText().trim());
        if (!due.getText().isBlank() && parsedDue == null) {
            JOptionPane.showMessageDialog(frame, "Enter a due date as YYYY-MM-DD.", "Check the due date", JOptionPane.WARNING_MESSAGE);
            editTask(task);
            return;
        }
        if (text.getText().isBlank()) {
            JOptionPane.showMessageDialog(frame, "Task text can’t be empty.", "Check the task", JOptionPane.WARNING_MESSAGE);
            editTask(task);
            return;
        }
        task.text = text.getText().trim();
        task.project = (String) project.getSelectedItem();
        task.priority = normalizePriority((String) priority.getSelectedItem());
        task.due = parsedDue;
        task.notes = notes.getText().trim();
        saveTasks();
        render();
    }

    private void clearCompleted() {
        boolean removed = tasks.removeIf(task -> task.done && matchesView(task));
        if (removed) saveTasks();
        render();
    }

    private void updateProgress() {
        LocalDate today = LocalDate.now();
        long doneToday = tasks.stream().filter(task -> today.equals(task.completedAt)).count();
        long dueToday = tasks.stream().filter(task -> today.equals(task.due)).count();
        long completeToday = tasks.stream().filter(task -> today.equals(task.due) && task.done).count();
        doneTodayLabel.setText(Long.toString(doneToday));
        sessionsLabel.setText(Integer.toString(focusSessions));
        progressLabel.setText(completeToday + " of " + dueToday);
        int percent = dueToday == 0 ? 0 : (int) (100 * completeToday / dueToday);
        progressBar.setToolTipText(percent + "% complete today");
        progressBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        progressBar.setLayout(new BorderLayout());
        progressBar.removeAll();
        JPanel fill = new JPanel();
        fill.setBackground(GREEN);
        fill.setPreferredSize(new Dimension(Math.max(0, percent * 2), 6));
        progressBar.add(fill, BorderLayout.WEST);
        progressBar.revalidate();
        progressBar.repaint();
    }

    private void toggleTimer() {
        if (timer.isRunning()) {
            remainingSeconds = Math.max(0, (int) Math.ceil((timerDeadline - System.currentTimeMillis()) / 1000.0));
            timer.stop();
            timerToggle.setText("Resume focus");
            timerModeLabel.setText("Paused");
            showTime();
            return;
        }
        if (remainingSeconds <= 0) remainingSeconds = timerDurationSeconds;
        timerDeadline = System.currentTimeMillis() + remainingSeconds * 1000L;
        timerToggle.setText("Pause");
        timerModeLabel.setText("Focus session");
        timer.start();
    }

    private void updateTimer() {
        remainingSeconds = Math.max(0, (int) Math.ceil((timerDeadline - System.currentTimeMillis()) / 1000.0));
        showTime();
        if (remainingSeconds == 0) {
            timer.stop();
            timerToggle.setText("Start focus");
            timerModeLabel.setText("Session complete");
            focusSessions++;
            saveFocusCount();
            updateProgress();
            JOptionPane.showMessageDialog(frame, "Focus session complete. Take a short break.", "Nice work", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showTime() {
        timerLabel.setText(String.format(Locale.ROOT, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
    }

    private void resetTimer() {
        timer.stop();
        remainingSeconds = timerDurationSeconds;
        timerToggle.setText("Start focus");
        timerModeLabel.setText("Focus session");
        showTime();
    }

    private void setTimerDuration() {
        Object selected = durationInput.getSelectedItem();
        if (selected == null) return;
        String valueText = selected.toString().trim();
        if (valueText.isEmpty()) return;
        try {
            int parsedSeconds = parseDurationSeconds(valueText);
            if (parsedSeconds <= 0) {
                throw new IllegalArgumentException();
            }
            timerDurationSeconds = parsedSeconds;
            durationInput.setSelectedItem(formatDurationForInput(parsedSeconds));
            resetTimer();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(frame, "Enter a valid duration like 30s, 15m, 1h, or 90.", "Check focus time", JOptionPane.WARNING_MESSAGE);
            durationInput.setSelectedItem(formatDurationForInput(timerDurationSeconds));
        }
    }

    private static int parseDurationSeconds(String rawValue) {
        String text = rawValue.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (text.isEmpty()) throw new IllegalArgumentException("empty");

        if (text.matches("\\d+")) {
            long minutes = Long.parseLong(text);
            if (minutes > Integer.MAX_VALUE / 60) throw new IllegalArgumentException("duration too long");
            return (int) (minutes * 60);
        }

        double totalSeconds = 0;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)([hms])").matcher(text);
        boolean found = false;
        int matchedLength = 0;
        while (matcher.find()) {
            if (matcher.start() != matchedLength) throw new IllegalArgumentException("unsupported format");
            found = true;
            matchedLength = matcher.end();
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            int multiplier = switch (unit) {
                case "h" -> 60 * 60;
                case "m" -> 60;
                case "s" -> 1;
                default -> 0;
            };
            double segmentSeconds = value * multiplier;
            if (!Double.isFinite(segmentSeconds) || segmentSeconds > Integer.MAX_VALUE - totalSeconds) {
                throw new IllegalArgumentException("duration too long");
            }
            totalSeconds += segmentSeconds;
        }
        if (!found || matchedLength != text.length()) throw new IllegalArgumentException("unsupported format");
        return (int) totalSeconds;
    }

    private static String formatDurationForInput(int totalSeconds) {
        if (totalSeconds % 3600 == 0) {
            return (totalSeconds / 3600) + "h";
        }
        if (totalSeconds % 60 == 0) {
            return (totalSeconds / 60) + "m";
        }
        return totalSeconds + "s";
    }

    private void loadTasks() {
        if (!Files.exists(TASK_FILE)) return;
        try {
            for (String line : Files.readAllLines(TASK_FILE, StandardCharsets.UTF_8)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 9) continue;
                try {
                    tasks.add(new Task(fields[0], decode(fields[5]), decode(fields[6]), decode(fields[7]),
                            fields[4].isEmpty() ? null : LocalDate.parse(fields[4]), decode(fields[8]),
                            Boolean.parseBoolean(fields[1]), Long.parseLong(fields[2]),
                            fields[3].isEmpty() ? null : LocalDate.parse(fields[3])));
                } catch (RuntimeException ignored) {
                    // Ignore a malformed row without losing other saved tasks.
                }
            }
        } catch (IOException exception) {
            showStorageError("Could not read your saved tasks.", exception);
        }
    }

    private void saveTasks() {
        try {
            Files.createDirectories(DATA_DIRECTORY);
            List<String> lines = new ArrayList<>();
            for (Task task : tasks) {
                lines.add(String.join("\t", task.id, Boolean.toString(task.done), Long.toString(task.createdAt),
                        task.completedAt == null ? "" : task.completedAt.toString(),
                        task.due == null ? "" : task.due.toString(), encode(task.text), encode(task.project),
                        encode(task.priority), encode(task.notes)));
            }
            writeAtomically(TASK_FILE, lines);
        } catch (IOException exception) {
            showStorageError("Could not save your tasks.", exception);
        }
    }

    private void loadFocusCount() {
        try {
            if (!Files.exists(FOCUS_FILE)) return;
            String[] fields = Files.readString(FOCUS_FILE, StandardCharsets.UTF_8).trim().split("\\t", -1);
            if (fields.length == 2 && LocalDate.parse(fields[0]).equals(LocalDate.now())) {
                focusDay = LocalDate.parse(fields[0]);
                focusSessions = Integer.parseInt(fields[1]);
            }
        } catch (IOException | RuntimeException ignored) {
            focusSessions = 0;
        }
    }

    private void saveFocusCount() {
        try {
            Files.createDirectories(DATA_DIRECTORY);
            writeAtomically(FOCUS_FILE, List.of(focusDay + "\t" + focusSessions));
        } catch (IOException exception) {
            showStorageError("Could not save the focus session count.", exception);
        }
    }

    private static void writeAtomically(Path destination, List<String> lines) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), "daylist-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void showStorageError(String message, Exception exception) {
        if (frame.isDisplayable()) JOptionPane.showMessageDialog(frame, message + "\n" + exception.getMessage(), "Daylist storage", JOptionPane.ERROR_MESSAGE);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static LocalDate parseDueDate(String text) {
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String normalizePriority(String priority) {
        return "No priority".equals(priority) ? "None" : priority;
    }

    private static String displayPriority(String priority) {
        return "None".equals(priority) ? "No priority" : priority;
    }

    private static int priorityRank(String priority) {
        return switch (priority) {
            case "High" -> 0;
            case "Medium" -> 1;
            case "Low" -> 2;
            default -> 3;
        };
    }

    private static boolean isOverdue(Task task) {
        return !task.done && task.due != null && task.due.isBefore(LocalDate.now());
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(WHITE);
        combo.setForeground(INK);
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        combo.setPreferredSize(new Dimension(130, 30));
    }

    private static void styleField(JTextField field) {
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(4, 7, 4, 7)));
        field.setPreferredSize(new Dimension(118, 30));
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(GREEN);
        button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 13, 9, 13));
        return button;
    }

    private static JButton textButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setForeground(color);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(5, 7, 5, 7));
        button.setContentAreaFilled(false);
        return button;
    }

    private static final class Task {
        private final String id;
        private String text;
        private String project;
        private String priority;
        private LocalDate due;
        private String notes;
        private boolean done;
        private final long createdAt;
        private LocalDate completedAt;

        private Task(String id, String text, String project, String priority, LocalDate due, String notes,
                     boolean done, long createdAt, LocalDate completedAt) {
            this.id = id;
            this.text = text;
            this.project = project;
            this.priority = priority;
            this.due = due;
            this.notes = notes;
            this.done = done;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
        }
    }
}