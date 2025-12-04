package app;

import app.ui.ConnectionStatusLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
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
    private final JButton btnLoginAction;

    // Dashboard Buttons
    private JButton btnBook;
    private JButton btnMyAppts; // For Users: History. For Employees: Pending.
    private JButton btnListEmps;
    private JButton btnAdminPanel; // For Admins
    private JPanel dashboardPanel;

    private Timer connectionTimer;

    public SchedulerGUI(String host, int port) {
        this.host = host;
        this.port = port;

        setTitle("Appointment Scheduler System");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header
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

        // Dashboard
        dashboardPanel = new JPanel(new GridBagLayout());
        dashboardPanel.setBackground(new Color(245, 245, 250));

        // Create buttons
        btnBook = createDashboardButton("Book Appointment", "📅");
        btnMyAppts = createDashboardButton("My Appointments", "📂"); // Text changes based on role
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
            lblWelcome.setText("Welcome, Guest");
            btnLoginAction.setText("Login");
            btnLoginAction.setBackground(new Color(40, 167, 69));
            JLabel info = new JLabel("<html><center><h2>Please Login to Manage Appointments</h2></center></html>");
            info.setForeground(Color.GRAY);
            dashboardPanel.add(info);
        } else {
            lblWelcome.setText("Welcome, " + loggedUsername + " (" + loggedRole + ")");
            btnLoginAction.setText("Logout");
            btnLoginAction.setBackground(new Color(220, 53, 69));

            // --- ROLE BASED UI ---
            if ("ADMIN".equalsIgnoreCase(loggedRole)) {
                // Admin sees Manage Users & View Employees
                gbc.gridx = 0; dashboardPanel.add(btnAdminPanel, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnListEmps, gbc);
            } else if ("EMPLOYEE".equalsIgnoreCase(loggedRole)) {
                // Employee sees Pending Approvals & View Employees (NO BOOKING)
                updateButtonText(btnMyAppts, "Approve Requests", "📝");
                gbc.gridx = 0; dashboardPanel.add(btnMyAppts, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnListEmps, gbc);
            } else {
                // Regular User sees Book, My History, View Employees
                updateButtonText(btnMyAppts, "My Appointments", "📂");
                gbc.gridx = 0; dashboardPanel.add(btnBook, gbc);
                gbc.gridx = 1; dashboardPanel.add(btnMyAppts, gbc);
                gbc.gridx = 2; dashboardPanel.add(btnListEmps, gbc);
            }
        }
        dashboardPanel.revalidate();
        dashboardPanel.repaint();
    }

    // --- ADMIN PANEL ---
    private void openAdminDialog() {
        JDialog dlg = new JDialog(this, "Admin Management", true);
        dlg.setLayout(new BorderLayout());

        // Table Columns: ID, Username, Role
        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"ID", "Username", "Role"}, 0);
        JTable table = new JTable(tableModel);

        // Fetch Users
        sendLine("ADMIN_LIST_USERS");
        try {
            String line;
            while ((line = readResponse()) != null) {
                if ("END".equals(line)) break;
                // USER ID|Username|Role
                if (line.startsWith("USER ")) {
                    String[] parts = line.substring(5).split("\\|");
                    tableModel.addRow(parts);
                }
            }
        } catch (IOException e) { JOptionPane.showMessageDialog(this, "Error fetching users"); return; }

        JPanel btnPanel = new JPanel();
        JButton btnAdd = new JButton("Add One Role");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDelete = new JButton("Delete Selected");

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnDelete);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        // Add Action
        btnAdd.addActionListener(e -> {
            JTextField tfUser = new JTextField(), tfRole = new JTextField("USER");
            JPasswordField pfPass = new JPasswordField();
            Object[] msg = {"Username:", tfUser, "Password:", pfPass, "Role (USER/EMPLOYEE/ADMIN):", tfRole};
            if (JOptionPane.showConfirmDialog(dlg, msg, "Add User", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                sendLine("ADMIN_ADD_USER " + tfUser.getText() + "|" + new String(pfPass.getPassword()) + "|" + tfRole.getText());
                try { JOptionPane.showMessageDialog(dlg, readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        // Edit Action
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
                sendLine("ADMIN_UPDATE_USER " + id + "|" + tfUser.getText() + "|" + new String(pfPass.getPassword()) + "|" + tfRole.getText());
                try { JOptionPane.showMessageDialog(dlg, readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        // Delete Action
        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String id = (String) tableModel.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(dlg, "Delete User ID " + id + "?") == JOptionPane.YES_OPTION) {
                sendLine("ADMIN_DELETE_USER " + id);
                try { JOptionPane.showMessageDialog(dlg, readResponse()); dlg.dispose(); openAdminDialog(); } catch(Exception ex){}
            }
        });

        dlg.setSize(600, 400);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // --- UTILS ---
    private void updateButtonText(JButton btn, String text, String icon) {
        btn.setText("<html><center><font size='6'>" + icon + "</font><br/><br/>" + text + "</center></html>");
    }

    private JButton createDashboardButton(String text, String iconEmoji) {
        JButton btn = new JButton();
        updateButtonText(btn, text, iconEmoji);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setForeground(new Color(50, 50, 50));
        btn.setPreferredSize(new Dimension(220, 180));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { if(btn.isEnabled()) btn.setBackground(new Color(235, 245, 255)); }
            public void mouseExited(java.awt.event.MouseEvent evt) { if(btn.isEnabled()) btn.setBackground(Color.WHITE); }
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

    private void handleLoginLogoutAction() {
        if (loggedUserId == null) showLoginDialog();
        else logout();
    }

    private void logout() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        loggedUserId = null; loggedUsername = null; loggedRole = null;
        updateDashboardState();
    }

    private void autoConnect() {
        connectionTimer = new Timer(true);
        connectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    boolean alive = isAlive();
                    if (!alive) {
                        SwingUtilities.invokeLater(connectionStatusLabel::startReconnecting);
                        SwingUtilities.invokeLater(() -> {
                            btnLoginAction.setEnabled(false);
                            if (loggedUserId != null) { loggedUserId = null; updateDashboardState(); }
                        });
                        tryConnect();
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            connectionStatusLabel.setConnected();
                            btnLoginAction.setEnabled(true);
                        });
                    }
                } catch (Exception ex) { System.err.println("ConnectionTimer error: " + ex.getMessage()); }
            }
        }, 0, 2000);
    }

    private boolean isAlive() {
        if (socket == null || socket.isClosed() || !socket.isConnected()) return false;
        try { out.println("PING"); if (out.checkError()) return false; return true; } catch (Exception e) { return false; }
    }

    private synchronized void tryConnect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            try { in.readLine(); } catch (IOException ignored) {}
            System.out.println("Connected to " + host + ":" + port);
        } catch (IOException e) {}
    }

    private String readResponse() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if ("PONG".equalsIgnoreCase(line.trim())) continue;
            return line;
        }
        return null;
    }

    private void sendLine(String s) { if (out != null) out.println(s); }

    // --- DIALOGS ---
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
            sendLine("LOGIN " + u + "|" + p);
            try {
                String r = readResponse();
                if (r != null && r.startsWith("OK ")) {
                    String[] parts = r.substring(3).split("\\|");
                    loggedUserId = Integer.parseInt(parts[0]);
                    loggedUsername = parts.length > 1 ? parts[1] : u;
                    loggedRole = parts.length > 2 ? parts[2] : "USER";
                    updateDashboardState();
                    dlg.dispose();
                } else JOptionPane.showMessageDialog(dlg, "Login Failed: " + r);
            } catch (IOException ex) { JOptionPane.showMessageDialog(dlg, "Network Error"); }
        });

        btnSignup.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty()) return;
            sendLine("REGISTER " + u + "|" + p + "|USER");
            try {
                String r = readResponse(); // Using readResponse here too
                if (r != null && r.startsWith("OK")) JOptionPane.showMessageDialog(dlg, "Registered! Please Login.");
                else JOptionPane.showMessageDialog(dlg, "Register Error: " + r);
            } catch (IOException ex) { JOptionPane.showMessageDialog(dlg, "Network Error"); }
        });
        dlg.setVisible(true);
    }

    private void openBookingDialog() {
        sendLine("LIST_EMPLOYEES");
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
            }
        } catch (IOException e) { return; }

        JDialog dlg = new JDialog(this, "Book Appointment", true);
        dlg.setLayout(new BorderLayout(10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Select Employee:"));
        JComboBox<String> cb = new JComboBox<>(employees.toArray(new String[0]));
        top.add(cb);
        dlg.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 3, 5, 5));
        grid.setBorder(new EmptyBorder(10, 10, 10, 10));
        ButtonGroup group = new ButtonGroup();
        List<JToggleButton> toggles = new ArrayList<>();
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
                    String date = parts[0], start = parts[1];
                    LocalTime st = LocalTime.parse(start);
                    String end = st.plusMinutes(30).toString();
                    sendLine("BOOK " + empId + "|" + date + "|" + start + "|" + end);
                    try {
                        String resp = readResponse();
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
        sendLine("MY_APPTS");
        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            String line;
            while ((line = readResponse()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("APPT ")) model.addElement(line.substring(5));
            }
        } catch (IOException e) { return; }

        String title = "EMPLOYEE".equalsIgnoreCase(loggedRole) ? "Approve Pending Requests" : "My Appointments";
        JDialog dlg = new JDialog(this, title, true);
        dlg.setLayout(new BorderLayout());
        JList<String> list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 14));
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);

        if ("EMPLOYEE".equalsIgnoreCase(loggedRole)) {
            JButton btnConf = new JButton("Approve Selected");
            btnConf.setBackground(new Color(40, 167, 69));
            btnConf.setForeground(Color.WHITE);
            btnConf.addActionListener(e -> {
                String val = list.getSelectedValue();
                if (val != null) {
                    String id = val.split("\\|")[0];
                    sendLine("CONFIRM " + id);
                    try { JOptionPane.showMessageDialog(dlg, readResponse()); dlg.dispose(); showAppointments(); } catch (Exception ex) {}
                }
            });
            dlg.add(btnConf, BorderLayout.SOUTH);
        }
        dlg.setSize(600, 400); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
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
        } catch (IOException e) { return; }
        JDialog dlg = new JDialog(this, "Employees", true);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String s : employees) model.addElement(s);
        JList<String> list = new JList<>(model);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        dlg.setSize(300, 400); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }
}