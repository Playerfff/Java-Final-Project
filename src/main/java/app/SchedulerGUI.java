package app;

import app.ui.ConnectionStatusLabel;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
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

    private final ConnectionStatusLabel connectionStatusLabel;
    private final JButton btnListEmps, btnBook, btnMyAppts, btnLogout;
    private Timer connectionTimer;

    public SchedulerGUI(String host, int port) {
        this.host = host;
        this.port = port;
        setTitle("Appointment Scheduler");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        connectionStatusLabel = new ConnectionStatusLabel();

        JPanel top = new JPanel(new BorderLayout());
        top.add(connectionStatusLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnListEmps = new JButton("List Employees");
        btnBook = new JButton("Book Slot");
        btnMyAppts = new JButton("My Appointments");
        btnLogout = new JButton("Logout");
        btnPanel.add(btnListEmps);
        btnPanel.add(btnBook);
        btnPanel.add(btnMyAppts);
        btnPanel.add(btnLogout);
        top.add(btnPanel, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        // disable until logged in
        setButtonsEnabled(false);

        btnListEmps.addActionListener(e -> showEmployeeDialog());
        btnBook.addActionListener(e -> openBookingDialog());
        btnMyAppts.addActionListener(e -> showAppointments());
        btnLogout.addActionListener(e -> logout());

        autoConnect();
        SwingUtilities.invokeLater(this::showLoginDialog);
    }

    private void setButtonsEnabled(boolean on) {
        btnListEmps.setEnabled(on);
        btnBook.setEnabled(on);
        btnMyAppts.setEnabled(on);
        btnLogout.setEnabled(on);
    }

    private void autoConnect() {
        connectionTimer = new Timer(true);
        connectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    boolean alive = isAlive();
                    if (!alive) {
                        SwingUtilities.invokeLater(() -> connectionStatusLabel.startReconnecting());
                        tryConnect();
                    } else {
                        SwingUtilities.invokeLater(() -> connectionStatusLabel.setConnected());
                    }
                } catch (Exception ex) {
                    System.err.println("ConnectionTimer error: " + ex.getMessage());
                }
            }
        }, 0, 3000);
    }

    private boolean isAlive() {
        if (socket == null) return false;
        if (socket.isClosed() || !socket.isConnected()) return false;
        try {
            // send ping and wait for pong
            out.println("PING");
            out.flush();
            socket.setSoTimeout(2000);
            String resp = in.readLine();
            if ("PONG".equalsIgnoreCase(resp)) {
                return true;
            } else {
                return false;
            }
        } catch (SocketTimeoutException toe) {
            return false;
        } catch (IOException ioe) {
            return false;
        }
    }

    private synchronized void tryConnect() {
        try {
            if (socket != null && socket.isConnected() && !socket.isClosed()) return;
            socket = new Socket(host, port);
            socket.setSoTimeout(2000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            // read welcome if present but don't block; setSoTimeout makes readLine throw if none
            try {
                String welcome = in.readLine();
                if (welcome != null) System.out.println("Server: " + welcome);
            } catch (IOException ignored) {
            }

            SwingUtilities.invokeLater(() -> connectionStatusLabel.setConnected());
            System.out.println("Connected to server " + host + ":" + port);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> connectionStatusLabel.setDisconnected());
        }
    }

    private void showLoginDialog() {
        if (loggedUserId != null) return; // already logged in

        JDialog dlg = new JDialog(this, "Login / Sign up", true);
        dlg.setLayout(new GridLayout(5, 2, 8, 8));
        JTextField tfUser = new JTextField();
        JPasswordField pf = new JPasswordField();
        JLabel msg = new JLabel(" ", SwingConstants.CENTER);
        msg.setForeground(Color.RED);

        JButton btnLogin = new JButton("Login");
        JButton btnSignup = new JButton("Sign up");

        dlg.add(new JLabel("Username:"));
        dlg.add(tfUser);
        dlg.add(new JLabel("Password:"));
        dlg.add(pf);
        dlg.add(btnLogin);
        dlg.add(btnSignup);
        dlg.add(msg);
        dlg.setSize(350, 220);
        dlg.setLocationRelativeTo(this);

        btnLogin.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Fill fields");
                return;
            }
            sendLine("LOGIN " + u + "|" + p);
            try {
                String r = in.readLine();
                if (r != null && r.startsWith("OK ")) {
                    loggedUserId = Integer.parseInt(r.substring(3).trim());
                    loggedUsername = u;
                    loggedRole =
                    setButtonsEnabled(true);
                    msg.setText("Login success");
                    dlg.dispose();
                } else {
                    msg.setText("Login failed");
                }
            } catch (IOException ex) {
                msg.setText("Network error");
            }
        });

        btnSignup.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Fill fields");
                return;
            }
            sendLine("REGISTER " + u + "|" + p + "|USER");
            try {
                String r = in.readLine();
                if (r != null && r.startsWith("OK")) msg.setText("Registered OK - please login");
                else msg.setText("Register failed");
            } catch (IOException ex) {
                msg.setText("Network error");
            }
        });

        dlg.setVisible(true);
    }

    private void showEmployeeDialog() {
        sendLine("LIST_EMPLOYEES");
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = in.readLine()) != null) {
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

    private void openBookingDialog() {
        // get employees
        sendLine("LIST_EMPLOYEES");
        List<String> employees = new ArrayList<>();
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("EMP ")) employees.add(line.substring(4));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to fetch employees", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog(this, "Book Slot", true);
        dlg.setLayout(new BorderLayout(8, 8));
        JComboBox<String> cb = new JComboBox<>(employees.toArray(new String[0]));
        dlg.add(cb, BorderLayout.NORTH);

        // simple grid of times (demo)
        JPanel grid = new JPanel(new GridLayout(6, 4, 6, 6));
        List<JToggleButton> toggles = new ArrayList<>();
        for (int d = 0; d < 6; d++) {
            for (int hour = 9; hour < 17; hour += 1) {
                String slot = String.format("2025-12-%02dT%02d:00", 1 + d, hour);
                JToggleButton t = new JToggleButton(slot);
                toggles.add(t);
                grid.add(t);
            }
        }
        dlg.add(new JScrollPane(grid), BorderLayout.CENTER);

        JButton btnConfirm = new JButton("Book");
        btnConfirm.addActionListener(e -> {
            String emp = (String) cb.getSelectedItem();
            if (emp == null) return;
            String empId = emp.split(":")[0];
            for (JToggleButton t : toggles) {
                if (t.isSelected()) {
                    sendLine("BOOK " + empId + "|" + t.getText());
                    try {
                        String resp = in.readLine();
                        JOptionPane.showMessageDialog(dlg, resp);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(dlg, "Network error");
                    }
                    break;
                }
            }
        });
        dlg.add(btnConfirm, BorderLayout.SOUTH);
        dlg.setSize(800, 500);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showAppointments() {
        sendLine("MY_APPTS");
        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            String line;
            while ((line = in.readLine()) != null) {
//                System.out.println("line: " + line);
                if ("END".equals(line)) break;
                if (line.startsWith("APPT ")) model.addElement(line.substring(5));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to read appointments");
            return;
        }
        JDialog dlg = new JDialog(this, "My Appointments", true);
        JList<String> list = new JList<>(model);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        dlg.setSize(500, 400);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void logout() {
        sendLine("QUIT");
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        loggedUserId = null;
        loggedUsername = null;
        setButtonsEnabled(false);
        SwingUtilities.invokeLater(() -> connectionStatusLabel.setDisconnected());
    }

    private void sendLine(String s) {
        if (out != null) out.println(s);
    }
}
