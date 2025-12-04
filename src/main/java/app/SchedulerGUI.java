package app;

import app.ui.ConnectionStatusLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class SchedulerGUI extends JFrame {
    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private volatile Integer loggedUserId = null;
    private volatile String loggedUsername = null;
    private volatile String loggedRole = null;

    // UI Components
    private final ConnectionStatusLabel connectionStatusLabel;
    private final JLabel lblWelcome;
    private final JButton btnLoginAction; // The top-right button (Login/Logout)

    // Dashboard Buttons
    private JButton btnBook;
    private JButton btnMyAppts;
    private JButton btnListEmps;
    private JPanel dashboardPanel;

    private Timer connectionTimer;

    public SchedulerGUI(String host, int port) {
        this.host = host;
        this.port = port;

        // 1. Setup Main Frame
        setTitle("Appointment Scheduler System");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Use a nice layout
        setLayout(new BorderLayout());

        // 2. Create Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(50, 50, 60)); // Dark Gray
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        connectionStatusLabel = new ConnectionStatusLabel();

        lblWelcome = new JLabel("Welcome, Guest");
        lblWelcome.setForeground(Color.WHITE);
        lblWelcome.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblWelcome.setBorder(new EmptyBorder(0, 20, 0, 0));

        btnLoginAction = new JButton("Login");
        styleButton(btnLoginAction, new Color(60, 140, 200));
        btnLoginAction.addActionListener(e -> handleLoginLogoutAction());

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftHeader.setOpaque(false);
        leftHeader.add(connectionStatusLabel);
        leftHeader.add(lblWelcome);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(btnLoginAction, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // 3. Create Dashboard (Center)
        dashboardPanel = new JPanel(new GridBagLayout());
        dashboardPanel.setBackground(new Color(245, 245, 250)); // Light Gray

        // Init Dashboard Buttons
        btnBook = createDashboardButton("Book New Appointment", "📅");
        btnMyAppts = createDashboardButton("My Appointments", "📂");
        btnListEmps = createDashboardButton("View Employees", "👥");

        // Add actions
        btnBook.addActionListener(e -> openBookingDialog());
        btnMyAppts.addActionListener(e -> showAppointments());
        btnListEmps.addActionListener(e -> showEmployeeDialog());

        // Initially add them but disable/hide based on state
        updateDashboardState();

        add(dashboardPanel, BorderLayout.CENTER);

        // 4. Start Logic
        autoConnect();
    }

    /**
     * Creates a large, beautiful tile button for the dashboard
     */
    private JButton createDashboardButton(String text, String iconEmoji) {
        JButton btn = new JButton("<html><center><font size='6'>" + iconEmoji + "</font><br/><br/>" + text + "</center></html>");
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setForeground(new Color(50, 50, 50));
        btn.setPreferredSize(new Dimension(220, 180));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        // Hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (btn.isEnabled()) btn.setBackground(new Color(235, 245, 255));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (btn.isEnabled()) btn.setBackground(Color.WHITE);
            }
        });
        return btn;
    }

    private void styleButton(JButton btn, Color bgColor) {
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    // --- State Management ---

    private void updateDashboardState() {
        dashboardPanel.removeAll();

        if (loggedUserId == null) {
            // GUEST VIEW
            lblWelcome.setText("Welcome, Guest");
            btnLoginAction.setText("Login");
            btnLoginAction.setBackground(new Color(40, 167, 69)); // Green for Login

            JLabel info = new JLabel("<html><center><h2>Please Login to Manage Appointments</h2></center></html>");
            info.setForeground(Color.GRAY);
            dashboardPanel.add(info);
        } else {
            // LOGGED IN VIEW
            lblWelcome.setText("Welcome, " + loggedUsername + " (" + loggedRole + ")");
            btnLoginAction.setText("Logout");
            btnLoginAction.setBackground(new Color(220, 53, 69)); // Red for Logout

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(20, 20, 20, 20);
            gbc.gridx = 0;
            dashboardPanel.add(btnListEmps, gbc);
            gbc.gridx = 1;
            dashboardPanel.add(btnBook, gbc);
            gbc.gridx = 2;
            dashboardPanel.add(btnMyAppts, gbc);
        }

        dashboardPanel.revalidate();
        dashboardPanel.repaint();
    }

    private void handleLoginLogoutAction() {
        if (loggedUserId == null) {
            showLoginDialog();
        } else {
            logout();
        }
    }

    private void logout() {
        // To logout, we simply close the socket.
        // The auto-reconnect will detect this, create a new clean connection,
        // and we will be back to Guest state.
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }

        loggedUserId = null;
        loggedUsername = null;
        loggedRole = null;
        updateDashboardState();
    }

    // --- Networking & Auto Reconnect ---

    private void autoConnect() {
        connectionTimer = new Timer(true);
        connectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Check if connected
                    boolean alive = isAlive();

                    if (!alive) {
                        // If disconnected, force UI update and try to connect
                        SwingUtilities.invokeLater(connectionStatusLabel::startReconnecting);

                        // Disable buttons while reconnecting
                        SwingUtilities.invokeLater(() -> {
                            btnLoginAction.setEnabled(false);
                            if (loggedUserId != null) {
                                // If we were logged in and lost connection, effectively logout UI
                                loggedUserId = null;
                                updateDashboardState();
                            }
                        });

                        tryConnect();
                    } else {
                        // Connected
                        SwingUtilities.invokeLater(() -> {
                            connectionStatusLabel.setConnected();
                            btnLoginAction.setEnabled(true);
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("ConnectionTimer error: " + ex.getMessage());
                }
            }
        }, 0, 2000); // Check every 2 seconds
    }

    private boolean isAlive() {
        if (socket == null || socket.isClosed() || !socket.isConnected()) return false;
        try {
            // Fast ping
            out.println("PING");
            if (out.checkError()) return false; // checkError detects closed socket
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void tryConnect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(5000); // 5 sec timeout
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read welcome
            try {
                in.readLine();
            } catch (IOException ignored) {
            }

            System.out.println("Connected to " + host + ":" + port);
        } catch (IOException e) {
            // Quiet fail, timer will retry
        }
    }

    /**
     * Reads a line from the server, skipping any "PONG" heartbeat messages.
     */
    private String readResponse() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if ("PONG".equalsIgnoreCase(line.trim())) continue; // Skip heartbeats
            return line;
        }
        return null;
    }

    private void sendLine(String s) {
        if (out != null) out.println(s);
    }

    // --- Dialogs (Login, Book, etc) ---

    private void showLoginDialog() {
        JDialog dlg = new JDialog(this, "Login System", true);
        dlg.setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridLayout(3, 2, 10, 10));
        center.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTextField tfUser = new JTextField();
        JPasswordField pf = new JPasswordField();

        center.add(new JLabel("Username:"));
        center.add(tfUser);
        center.add(new JLabel("Password:"));
        center.add(pf);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnLogin = new JButton("Login");
        JButton btnSignup = new JButton("Register");

        bottom.add(btnSignup);
        bottom.add(btnLogin);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setSize(400, 250);
        dlg.setLocationRelativeTo(this);

        btnLogin.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty()) return;

            sendLine("LOGIN " + u + "|" + p);
            try {
                String r = readResponse();
                if (r != null && r.startsWith("OK ")) {
                    // OK ID|Username|Role
                    String[] parts = r.substring(3).split("\\|");
                    loggedUserId = Integer.parseInt(parts[0]);
                    loggedUsername = parts.length > 1 ? parts[1] : u;
                    loggedRole = parts.length > 2 ? parts[2] : "USER";

                    updateDashboardState();
                    dlg.dispose();
                } else {
                    JOptionPane.showMessageDialog(dlg, "Login Failed: " + r);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Network Error");
            }
        });

        btnSignup.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty()) return;

            sendLine("REGISTER " + u + "|" + p + "|USER");
            try {
                String r = in.readLine();
                if (r != null && r.startsWith("OK")) JOptionPane.showMessageDialog(dlg, "Registered! Please Login.");
                else JOptionPane.showMessageDialog(dlg, "Register Error: " + r);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Network Error");
            }
        });

        dlg.setVisible(true);
    }

    // --- Other Dialogs (Simplified for brevity, logic copied from previous steps) ---

    private void openBookingDialog() {
        // Fetch employees
        sendLine("LIST_EMPLOYEES");
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
            }
        } catch (IOException e) {
            return;
        }

        JDialog dlg = new JDialog(this, "Book Appointment", true);
        dlg.setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Select Employee:"));
        JComboBox<String> cb = new JComboBox<>(employees.toArray(new String[0]));
        top.add(cb);
        dlg.add(top, BorderLayout.NORTH);

        // Generate Slots (Next 5 Days)
        JPanel grid = new JPanel(new GridLayout(0, 3, 5, 5));
        grid.setBorder(new EmptyBorder(10, 10, 10, 10));
        ButtonGroup group = new ButtonGroup();
        List<JToggleButton> toggles = new ArrayList<>();

        LocalDate startDate = LocalDate.now().plusDays(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < 5; i++) {
            LocalDate d = startDate.plusDays(i);
            for (int h = 9; h < 18; h++) {
                if (h == 12) continue; // Lunch
                String t1 = String.format("%02d:00", h);
                String label = d.format(fmt) + " " + t1;
                JToggleButton btn = new JToggleButton(label);
                group.add(btn);
                toggles.add(btn);
                grid.add(btn);
            }
        }
        dlg.add(new JScrollPane(grid), BorderLayout.CENTER);

        JButton btnConfirm = new JButton("Confirm Booking");
        btnConfirm.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConfirm.setBackground(new Color(40, 167, 69));
        btnConfirm.setForeground(Color.WHITE);

        btnConfirm.addActionListener(e -> {
            String emp = (String) cb.getSelectedItem();
            if (emp == null) return;
            String empId = emp.split(":")[0];

            for (JToggleButton t : toggles) {
                if (t.isSelected()) {
                    String[] parts = t.getText().split(" ");
                    String date = parts[0];
                    String start = parts[1];
                    LocalTime st = LocalTime.parse(start);
                    String end = st.plusMinutes(30).toString();

                    sendLine("BOOK " + empId + "|" + date + "|" + start + "|" + end);
                    try {
                        String resp = readResponse();
                        JOptionPane.showMessageDialog(dlg, resp);
                        if (resp.startsWith("OK")) dlg.dispose();
                    } catch (Exception ex) {
                    }
                    return;
                }
            }
        });

        dlg.add(btnConfirm, BorderLayout.SOUTH);
        dlg.setSize(800, 600);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showAppointments() {
        sendLine("MY_APPTS");
        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            String line;
            while ((line = readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("APPT ")) model.addElement(line.substring(5));
            }
        } catch (IOException e) {
            return;
        }

        JDialog dlg = new JDialog(this, "My Appointments", true);
        dlg.setLayout(new BorderLayout());
        JList<String> list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 14));
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);

        if ("EMPLOYEE".equalsIgnoreCase(loggedRole)) {
            JButton btnConf = new JButton("Confirm Selected");
            btnConf.addActionListener(e -> {
                String val = list.getSelectedValue();
                if (val != null) {
                    String id = val.split("\\|")[0];
                    sendLine("CONFIRM " + id);
                    try {
                        JOptionPane.showMessageDialog(dlg, in.readLine());
                    } catch (Exception ex) {
                    }
                }
            });
            dlg.add(btnConf, BorderLayout.SOUTH);
        }

        dlg.setSize(600, 400);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showEmployeeDialog() {
        sendLine("LIST_EMPLOYEES");
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
                if (line.startsWith("ERROR")) break;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to read employees", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JDialog dlg = new JDialog(this, "Employees", true);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String s : employees) model.addElement(s);
        JList<String> list = new JList<>(model);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        dlg.setSize(300, 400);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
}