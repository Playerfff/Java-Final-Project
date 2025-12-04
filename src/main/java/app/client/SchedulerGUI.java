package app.client;

import app.common.Protocol;
import app.common.models.Role;
import app.client.ui.ConnectionStatusLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class SchedulerGUI extends JFrame {
    private final String host;
    private final int port;

    // Refactoring 3: Use a helper class for networking
    private ServerConnection server;

    private volatile Integer loggedUserId = null;
    private volatile String loggedUsername = null;
    // Refactoring 1: Use Enum instead of String
    private volatile Role loggedRole = null;

    // UI Components
    private final ConnectionStatusLabel connectionStatusLabel;
    private final JLabel lblWelcome;
    private final JButton btnLoginAction;

    // Dashboard Buttons
    private JButton btnBook;
    private JButton btnMyAppts;
    private JButton btnListEmps;
    private JButton btnAdminPanel;
    private JPanel dashboardPanel;

    private Timer connectionTimer;

    public SchedulerGUI(String host, int port) {
        this.host = host;
        this.port = port;
        this.server = new ServerConnection(host, port);

        setTitle("Appointment Scheduler System");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(50, 50, 60));
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

        // --- Dashboard ---
        dashboardPanel = new JPanel(new GridBagLayout());
        dashboardPanel.setBackground(new Color(245, 245, 250));

        btnBook = createDashboardButton("Book Appointment", "📅");
        btnMyAppts = createDashboardButton("My Appointments", "📂");
        btnListEmps = createDashboardButton("View Employees", "👥");
        btnAdminPanel = createDashboardButton("Manage Users", "🛠️");

        btnBook.addActionListener(e -> openBookingDialog());
        btnMyAppts.addActionListener(e -> showAppointments());
        btnListEmps.addActionListener(e -> showEmployeeDialog());
        btnAdminPanel.addActionListener(e -> openAdminDialog());

        updateDashboardState();
        add(dashboardPanel, BorderLayout.CENTER);

        autoConnect();
    }

    private void updateDashboardState() {
        dashboardPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);

        if (loggedUserId == null) {
            // Guest View
            lblWelcome.setText("Welcome, Guest");
            btnLoginAction.setText("Login");
            btnLoginAction.setBackground(new Color(40, 167, 69));
            JLabel info = new JLabel("<html><center><h2>Please Login to Manage Appointments</h2></center></html>");
            info.setForeground(Color.GRAY);
            dashboardPanel.add(info);
        } else {
            // Logged In View
            lblWelcome.setText("Welcome, " + loggedUsername + " (" + loggedRole + ")");
            btnLoginAction.setText("Logout");
            btnLoginAction.setBackground(new Color(220, 53, 69));

            // Role-Based Dashboard Logic
            if (loggedRole == Role.ADMIN) {
                gbc.gridx = 0; dashboardPanel.add(btnAdminPanel, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnListEmps, gbc);
            } else if (loggedRole == Role.EMPLOYEE) {
                updateButtonText(btnMyAppts, "Approve Requests", "📝");
                gbc.gridx = 0; dashboardPanel.add(btnMyAppts, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnListEmps, gbc);
            } else {
                updateButtonText(btnMyAppts, "My Appointments", "📂");
                gbc.gridx = 0; dashboardPanel.add(btnBook, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnMyAppts, gbc);
                gbc.gridx = 2; dashboardPanel.add(btnListEmps, gbc);
            }
        }
        dashboardPanel.revalidate();
        dashboardPanel.repaint();
    }

    // --- Admin Feature ---
    private void openAdminDialog() {
        JDialog dlg = new JDialog(this, "Admin Management", true);
        dlg.setLayout(new BorderLayout());

        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"ID", "Username", "Role"}, 0);
        JTable table = new JTable(tableModel);

        // Fetch Users using Protocol constant
        server.send(Protocol.CMD_ADMIN_LIST);
        try {
            String line;
            while ((line = server.readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("USER ")) {
                    String[] parts = line.substring(5).split("\\|");
                    tableModel.addRow(parts);
                }
            }
        } catch (Exception e) { return; }

        JPanel btnPanel = new JPanel();
        JButton btnAdd = new JButton("Add User");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDelete = new JButton("Delete Selected");

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnDelete);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> {
            JTextField tfUser = new JTextField(), tfRole = new JTextField("USER");
            JPasswordField pfPass = new JPasswordField();
            Object[] msg = {"Username:", tfUser, "Password:", pfPass, "Role (USER/EMPLOYEE/ADMIN):", tfRole};
            if (JOptionPane.showConfirmDialog(dlg, msg, "Add User", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                String cmd = Protocol.CMD_ADMIN_ADD + " " + tfUser.getText() + "|" + new String(pfPass.getPassword()) + "|" + tfRole.getText();
                server.send(cmd);
                try { JOptionPane.showMessageDialog(dlg, server.readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String id = (String) tableModel.getValueAt(row, 0);
            String currUser = (String) tableModel.getValueAt(row, 1);
            String currRole = (String) tableModel.getValueAt(row, 2);

            JTextField tfUser = new JTextField(currUser), tfRole = new JTextField(currRole);
            JPasswordField pfPass = new JPasswordField();
            Object[] msg = {"Username:", tfUser, "New Password (leave empty to keep):", pfPass, "Role:", tfRole};
            if (JOptionPane.showConfirmDialog(dlg, msg, "Edit User", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                String cmd = Protocol.CMD_ADMIN_UPDATE + " " + id + "|" + tfUser.getText() + "|" + new String(pfPass.getPassword()) + "|" + tfRole.getText();
                server.send(cmd);
                try { JOptionPane.showMessageDialog(dlg, server.readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String id = (String) tableModel.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(dlg, "Delete User ID " + id + "?") == JOptionPane.YES_OPTION) {
                server.send(Protocol.CMD_ADMIN_DELETE + " " + id);
                try { JOptionPane.showMessageDialog(dlg, server.readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        dlg.setSize(600, 400); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    // --- Networking ---
    private void autoConnect() {
        connectionTimer = new Timer(true);
        connectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!server.isConnected()) {
                    SwingUtilities.invokeLater(() -> {
                        connectionStatusLabel.startReconnecting();
                        btnLoginAction.setEnabled(false);
                        if (loggedUserId != null) { loggedUserId = null; updateDashboardState(); }
                    });
                    if (server.connect()) {
                        SwingUtilities.invokeLater(() -> {
                            connectionStatusLabel.setConnected();
                            btnLoginAction.setEnabled(true);
                        });
                    }
                }
            }
        }, 0, 2000);
    }

    private void handleLoginLogoutAction() {
        if (loggedUserId == null) showLoginDialog();
        else {
            server.disconnect(); // Explicitly disconnect
            loggedUserId = null; loggedUsername = null; loggedRole = null;
            updateDashboardState();
        }
    }

    // --- Dialogs ---
    private void showLoginDialog() {
        JDialog dlg = new JDialog(this, "Login System", true);
        dlg.setLayout(new BorderLayout());
        JPanel center = new JPanel(new GridLayout(3, 2, 10, 10));
        center.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTextField tfUser = new JTextField();
        JPasswordField pf = new JPasswordField();
        center.add(new JLabel("Username:")); center.add(tfUser);
        center.add(new JLabel("Password:")); center.add(pf);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnLogin = new JButton("Login");
        JButton btnSignup = new JButton("Register");
        bottom.add(btnSignup); bottom.add(btnLogin);
        dlg.add(center, BorderLayout.CENTER); dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setSize(400, 250); dlg.setLocationRelativeTo(this);

        btnLogin.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty()) return;

            // Refactoring 2: Use Protocol
            server.send(Protocol.CMD_LOGIN + " " + u + "|" + p);
            try {
                String r = server.readResponse();
                if (r != null && r.startsWith("OK ")) {
                    // Response: OK ID|Username|Role
                    String[] parts = r.substring(3).split("\\|");
                    loggedUserId = Integer.parseInt(parts[0]);
                    loggedUsername = parts.length > 1 ? parts[1] : u;
                    // Parse Role
                    try {
                        loggedRole = Role.valueOf(parts.length > 2 ? parts[2].toUpperCase() : "USER");
                    } catch (IllegalArgumentException ex) { loggedRole = Role.USER; }

                    updateDashboardState();
                    dlg.dispose();
                } else JOptionPane.showMessageDialog(dlg, "Login Failed: " + r);
            } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Network Error"); }
        });

        btnSignup.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty()) return;
            server.send(Protocol.CMD_REGISTER + " " + u + "|" + p + "|USER");
            try {
                String r = server.readResponse();
                if (r != null && r.startsWith("OK")) JOptionPane.showMessageDialog(dlg, "Registered! Please Login.");
                else JOptionPane.showMessageDialog(dlg, "Register Error: " + r);
            } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Network Error"); }
        });
        dlg.setVisible(true);
    }

    private void openBookingDialog() {
        server.send(Protocol.CMD_LIST_EMPS);
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = server.readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
            }
        } catch (Exception e) { return; }

        JDialog dlg = new JDialog(this, "Book Appointment", true);
        dlg.setLayout(new BorderLayout(10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Select Employee:"));
        JComboBox<String> cb = new JComboBox<>(employees.toArray(new String[0]));
        top.add(cb);
        dlg.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 3, 5, 5));
        grid.setBorder(new EmptyBorder(10, 10, 10, 10));
        List<JToggleButton> toggles = new ArrayList<>();
        ButtonGroup group = new ButtonGroup();

        LocalDate startDate = LocalDate.now().plusDays(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < 5; i++) {
            LocalDate d = startDate.plusDays(i);
            for (int h = 9; h < 18; h++) {
                if (h == 12) continue;
                String t1 = String.format("%02d:00", h);
                String label = d.format(fmt) + " " + t1;
                JToggleButton btn = new JToggleButton(label);
                group.add(btn); toggles.add(btn); grid.add(btn);
            }
        }
        dlg.add(new JScrollPane(grid), BorderLayout.CENTER);

        JButton btnConfirm = new JButton("Confirm Booking");
        btnConfirm.setBackground(new Color(40, 167, 69));
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.addActionListener(e -> {
            String emp = (String) cb.getSelectedItem();
            if (emp == null) return;
            String empId = emp.split(":")[0];
            for (JToggleButton t : toggles) {
                if (t.isSelected()) {
                    String[] parts = t.getText().split(" ");
                    String start = parts[1];
                    String end = LocalTime.parse(start).plusMinutes(30).toString();
                    server.send(Protocol.CMD_BOOK + " " + empId + "|" + parts[0] + "|" + start + "|" + end);
                    try {
                        String resp = server.readResponse();
                        JOptionPane.showMessageDialog(dlg, resp);
                        if (resp.startsWith("OK")) dlg.dispose();
                    } catch (Exception ex) {}
                    return;
                }
            }
        });
        dlg.add(btnConfirm, BorderLayout.SOUTH);
        dlg.setSize(800, 600); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    private void showAppointments() {
        server.send(Protocol.CMD_MY_APPTS);
        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            String line;
            while ((line = server.readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("APPT ")) model.addElement(line.substring(5));
            }
        } catch (Exception e) { return; }

        JDialog dlg = new JDialog(this, "Appointments", true);
        dlg.setLayout(new BorderLayout());
        JList<String> list = new JList<>(model);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);

        if (loggedRole == Role.EMPLOYEE) {
            JButton btnConf = new JButton("Approve Selected");
            btnConf.addActionListener(e -> {
                String val = list.getSelectedValue();
                if (val != null) {
                    String id = val.split("\\|")[0];
                    server.send(Protocol.CMD_CONFIRM + " " + id);
                    try { JOptionPane.showMessageDialog(dlg, server.readResponse()); dlg.dispose(); showAppointments(); } catch (Exception ex) {}
                }
            });
            dlg.add(btnConf, BorderLayout.SOUTH);
        }
        dlg.setSize(600, 400); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    private void showEmployeeDialog() {
        server.send(Protocol.CMD_LIST_EMPS);
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = server.readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
            }
        } catch (Exception e) { return; }
        JDialog dlg = new JDialog(this, "Employees", true);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String s : employees) model.addElement(s);
        dlg.add(new JScrollPane(new JList<>(model)), BorderLayout.CENTER);
        dlg.setSize(300, 400); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    // --- Helpers ---
    private JButton createDashboardButton(String text, String icon) {
        JButton btn = new JButton();
        updateButtonText(btn, text, icon);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setPreferredSize(new Dimension(220, 180));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        return btn;
    }

    private void updateButtonText(JButton btn, String text, String icon) {
        btn.setText("<html><center><font size='6'>" + icon + "</font><br/><br/>" + text + "</center></html>");
    }

    private void styleButton(JButton btn, Color bgColor) {
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }
}