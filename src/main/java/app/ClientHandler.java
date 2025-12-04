package app;

import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.mappers.AppointmentMapper;
import app.models.Appointment;
import app.models.User;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Integer loggedUserId = null;
    private String loggedUserRole = null; // Store Role

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (var s = socket) {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(s.getOutputStream(), true);

            out.println("WELCOME AppointmentSystem");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if ("PING".equalsIgnoreCase(line)) {
                    continue;
                }
                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase();
                String payload = parts.length > 1 ? parts[1] : "";

                switch (cmd) {
                    case "REGISTER": handleRegister(payload); break;
                    case "LOGIN":    handleLogin(payload); break;
                    case "LIST_EMPLOYEES": handleListEmployees(); break;
                    case "BOOK":     handleBook(payload); break;
                    case "MY_APPTS": handleMyAppts(); break; // Consolidated
                    case "CONFIRM":  handleConfirm(payload); break; // NEW COMMAND
                    case "MY_INFO":  handleMyInfo(); break;
                    case "QUIT":
                        out.println("OK BYE");
                        return;
                    default:
                        out.println("ERROR UnknownCommand");
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection closed: " + e.getMessage());
        }
    }

    // ... handleRegister and handleListEmployees remain the same ...
    private void handleRegister(String payload) {
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            String[] p = payload.split("\\|");
            if (p.length < 2) { out.println("ERROR BadPayload"); return; }
            String username = p[0];
            String password = p[1];
            String role = p.length >= 3 ? p[2] : "USER";
            if (um.findByUsername(username) != null) { out.println("ERROR Exists"); return; }
            String salt = Utils.randomSaltBase64(16);
            String hash = Utils.hashPassword(password, salt);
            User u = new User();
            u.setUsername(username); u.setSalt(salt); u.setHash(hash); u.setRole(role);
            um.insertUser(u);
            out.println("OK Registered");
        } catch (Exception e) { out.println("ERROR RegisterFailed"); }
    }

    private void handleListEmployees() {
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            List<Map<String, Object>> emps = um.listEmployees();
            out.println("OK COUNT " + emps.size());
            for (Map<String, Object> m : emps) {
                out.println("EMP " + m.get("id") + ":" + m.get("username"));
            }
            out.println("END");
        } catch (Exception e) { out.println("ERROR ListEmps"); }
    }

    private void handleLogin(String payload) {
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            String[] p = payload.split("\\|");
            if (p.length < 2) { out.println("ERROR BadPayload"); return; }
            String username = p[0], password = p[1];
            User u = um.findByUsername(username);
            if (u == null) { out.println("ERROR AuthFailed"); return; }

            String computed = Utils.hashPassword(password, u.getSalt());
            if (computed.equals(u.getHash())) {
                loggedUserId = u.getId();
                loggedUserRole = u.getRole();
                // Send ID|Username|Role so Client knows if it can confirm
                out.println("OK " + loggedUserId + "|" + u.getUsername() + "|" + loggedUserRole);
            } else out.println("ERROR AuthFailed");
        } catch (Exception e) { out.println("ERROR AuthError"); }
    }

    private void handleBook(String payload) {
        if (loggedUserId == null) { out.println("ERROR NotLoggedIn"); return; }
        String[] p = payload.split("\\|");
        if (p.length < 4) { out.println("ERROR BadPayload"); return; }

        try {
            int empId = Integer.parseInt(p[0]);
            String dateStr = p[1], startStr = p[2], endStr = p[3];

            // Validation (simplified for brevity, logic remains same as previous step)
            LocalDate date = LocalDate.parse(dateStr);
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            if (start.getHour() < 9 || end.isAfter(LocalTime.of(18, 0))) {
                out.println("ERROR OutsideWorkingHours"); return;
            }
            if (start.isBefore(LocalTime.of(13,0)) && end.isAfter(LocalTime.of(12,0))) {
                out.println("ERROR LunchBreak"); return;
            }

            try (var session = MyBatisUtil.openSession()) {
                var am = session.getMapper(AppointmentMapper.class);
                Map<String, Object> params = new HashMap<>();
                params.put("employeeId", empId);
                params.put("date", dateStr);
                params.put("startTime", startStr);
                params.put("endTime", endStr);

                Integer conflict = am.findConflict(params);
                if (conflict != null) { out.println("ERROR SlotTaken"); return; }

                Appointment a = new Appointment();
                a.setUserId(loggedUserId);
                a.setEmployeeId(empId);
                a.setDate(dateStr);
                a.setStartTime(startStr);
                a.setEndTime(endStr);
                a.setStatus("PENDING"); // <--- CHANGED TO PENDING

                am.insertAppointment(a);
                out.println("OK Booked (Pending Confirmation)");
            }
        } catch (Exception e) { out.println("ERROR InvalidData"); }
    }

    private void handleMyAppts() {
        if (loggedUserId == null) { out.println("ERROR NotLoggedIn"); return; }
        try (var session = MyBatisUtil.openSession()) {
            var am = session.getMapper(app.mappers.AppointmentMapper.class);
            var um = session.getMapper(app.mappers.UserMapper.class);

            List<Appointment> list;
            // Fetch based on Role
            if ("EMPLOYEE".equalsIgnoreCase(loggedUserRole)) {
                list = am.listByEmployee(loggedUserId);
            } else {
                list = am.listByUser(loggedUserId);
            }

            out.println("OK COUNT " + list.size());
            for (Appointment a : list) {
                // Return relevant name (If I am user, show Emp Name. If I am Emp, show User Name)
                String otherName;
                if ("EMPLOYEE".equalsIgnoreCase(loggedUserRole)) {
                    otherName = um.usernameById(a.getUserId());
                } else {
                    otherName = um.usernameById(a.getEmployeeId());
                }
                out.println("APPT " + a.getId() + "|" + otherName + "|" + a.getDate() + "|" + a.getStartTime() + "|" + a.getStatus());
            }
            out.println("END");
        } catch (Exception e) { out.println("ERROR ApptsFailed"); }
    }

    private void handleConfirm(String payload) {
        if (loggedUserId == null) { out.println("ERROR NotLoggedIn"); return; }
        if (!"EMPLOYEE".equalsIgnoreCase(loggedUserRole)) { out.println("ERROR PermissionDenied"); return; }

        try (var session = MyBatisUtil.openSession()) {
            var am = session.getMapper(AppointmentMapper.class);
            int apptId = Integer.parseInt(payload);

            Map<String, Object> params = new HashMap<>();
            params.put("id", apptId);
            params.put("status", "CONFIRMED");

            am.updateStatus(params);
            out.println("OK Confirmed");
        } catch (Exception e) { out.println("ERROR ConfirmFailed"); }
    }

    private void handleMyInfo() {
        if (loggedUserId == null) { out.println("ERROR NotLoggedIn"); return; }
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            User u = um.findByUsername(um.usernameById(loggedUserId));
            out.println("OK " + u.getUsername() + " " + u.getRole());
            out.println("END");
        } catch (Exception e) { out.println("ERROR GetInfoFailed"); }
    }
}