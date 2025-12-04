package app.server;

import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.common.Protocol;
import app.server.mappers.AppointmentMapper;
import app.common.models.Appointment;
import app.common.models.User;
import app.server.mappers.UserMapper;
import org.apache.ibatis.session.SqlSession;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Integer loggedUserId = null;
    private String loggedUserRole = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (Socket s = socket) {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(s.getOutputStream(), true);

            out.println("WELCOME AppointmentSystem");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("PING".equalsIgnoreCase(line)) continue;

                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase();
                String payload = parts.length > 1 ? parts[1] : "";

                switch (cmd) {
                    case Protocol.CMD_REGISTER:
                        handleRegister(payload);
                        break;
                    case Protocol.CMD_LOGIN:
                        handleLogin(payload);
                        break;
                    case Protocol.CMD_LIST_EMPS:
                        handleListEmployees();
                        break;
                    case Protocol.CMD_BOOK:
                        handleBook(payload);
                        break;
                    case Protocol.CMD_MY_APPTS:
                        handleMyAppts();
                        break;
                    case Protocol.CMD_CONFIRM:
                        handleConfirm(payload);
                        break;
                    case "MY_INFO":
                        handleMyInfo();
                        break;
                    // --- Admin Commands ---
                    case Protocol.CMD_ADMIN_LIST:
                        handleAdminListUsers();
                        break;
                    case Protocol.CMD_ADMIN_ADD:
                        handleAdminAddUser(payload);
                        break;
                    case Protocol.CMD_ADMIN_UPDATE:
                        handleAdminUpdateUser(payload);
                        break;
                    case Protocol.CMD_ADMIN_DELETE:
                        handleAdminDeleteUser(payload);
                        break;

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

    private void handleRegister(String payload) {
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            String[] p = payload.split("\\|");
            if (p.length < 2) {
                out.println("ERROR BadPayload");
                return;
            }
            String username = p[0], password = p[1];
            String role = p.length >= 3 ? p[2] : "USER";

            if (um.findByUsername(username) != null) {
                out.println("ERROR Exists");
                return;
            }

            String salt = Utils.randomSaltBase64(16);
            String hash = Utils.hashPassword(password, salt);
            User u = new User();
            u.setUsername(username);
            u.setSalt(salt);
            u.setHash(hash);
            u.setRole(role);
            um.insertUser(u);
            out.println("OK Registered");
        } catch (Exception e) {
            out.println("ERROR RegisterFailed");
        }
    }

    private void handleLogin(String payload) {
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            String[] p = payload.split("\\|");
            if (p.length < 2) {
                out.println("ERROR BadPayload");
                return;
            }
            String username = p[0], password = p[1];
            User u = um.findByUsername(username);
            if (u == null) {
                out.println("ERROR AuthFailed");
                return;
            }

            String computed = Utils.hashPassword(password, u.getSalt());
            if (computed.equals(u.getHash())) {
                loggedUserId = u.getId();
                loggedUserRole = u.getRole();
                out.println("OK " + loggedUserId + "|" + u.getUsername() + "|" + loggedUserRole);
            } else out.println("ERROR AuthFailed");
        } catch (Exception e) {
            out.println("ERROR AuthError");
        }
    }

    // --- ADMIN HANDLERS ---
    private void handleAdminListUsers() {
        if (!"ADMIN".equals(loggedUserRole)) {
            out.println("ERROR Denied");
            return;
        }
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            List<User> users = um.findAll();
            out.println("OK COUNT " + users.size());
            for (User u : users) {
                out.println("USER " + u.getId() + "|" + u.getUsername() + "|" + u.getRole());
            }
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR ListFailed");
        }
    }

    private void handleAdminAddUser(String payload) {
        if (!"ADMIN".equals(loggedUserRole)) {
            out.println("ERROR Denied");
            return;
        }
        // Reuse register logic but strictly for admin
        handleRegister(payload);
    }

    private void handleAdminUpdateUser(String payload) {
        if (!"ADMIN".equals(loggedUserRole)) {
            out.println("ERROR Denied");
            return;
        }
        // Payload: ID|Username|Password|Role (Password can be empty to keep existing)
        String[] p = payload.split("\\|");
        if (p.length < 4) {
            out.println("ERROR BadPayload");
            return;
        }

        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            int id = Integer.parseInt(p[0]);
            String username = p[1];
            String password = p[2];
            String role = p[3];

            User u = um.findById(id);
            if (u == null) {
                out.println("ERROR NotFound");
                return;
            }

            u.setUsername(username);
            u.setRole(role);

            // Only update password if not empty
            if (!password.trim().isEmpty()) {
                String salt = Utils.randomSaltBase64(16);
                String hash = Utils.hashPassword(password, salt);
                u.setSalt(salt);
                u.setHash(hash);
            }

            um.updateUser(u);
            out.println("OK Updated");
        } catch (Exception e) {
            out.println("ERROR UpdateFailed");
        }
    }

    private void handleAdminDeleteUser(String payload) {
        if (!"ADMIN".equals(loggedUserRole)) {
            out.println("ERROR Denied");
            return;
        }
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            int id = Integer.parseInt(payload);
            um.deleteUser(id);
            out.println("OK Deleted");
        } catch (Exception e) {
            out.println("ERROR DeleteFailed");
        }
    }

    // --- OTHER HANDLERS ---
    private void handleListEmployees() {
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            List<Map<String, Object>> emps = um.listEmployees();
            out.println("OK COUNT " + emps.size());
            for (Map<String, Object> m : emps) {
                out.println("EMP " + m.get("id") + ":" + m.get("username"));
            }
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR ListEmps");
        }
    }

    private void handleBook(String payload) {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        String[] p = payload.split("\\|");
        if (p.length < 4) {
            out.println("ERROR BadPayload");
            return;
        }
        try {
            int empId = Integer.parseInt(p[0]);
            String dateStr = p[1], startStr = p[2], endStr = p[3];
            LocalDate date = LocalDate.parse(dateStr);
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);

            if (start.getHour() < 9 || end.isAfter(LocalTime.of(18, 0))) {
                out.println("ERROR OutsideWorkingHours");
                return;
            }
            if (start.isBefore(LocalTime.of(13, 0)) && end.isAfter(LocalTime.of(12, 0))) {
                out.println("ERROR LunchBreak");
                return;
            }

            try (SqlSession session = MyBatisUtil.openSession()) {
                AppointmentMapper am = session.getMapper(AppointmentMapper.class);
                Map<String, Object> params = new HashMap<>();
                params.put("employeeId", empId);
                params.put("date", dateStr);
                params.put("startTime", startStr);
                params.put("endTime", endStr);
                if (am.findConflict(params) != null) {
                    out.println("ERROR SlotTaken");
                    return;
                }

                Appointment a = new Appointment();
                a.setUserId(loggedUserId);
                a.setEmployeeId(empId);
                a.setDate(dateStr);
                a.setStartTime(startStr);
                a.setEndTime(endStr);
                a.setStatus("PENDING");
                am.insertAppointment(a);
                out.println("OK Booked (Pending Confirmation)");
            }
        } catch (Exception e) {
            out.println("ERROR InvalidData");
        }
    }

    private void handleMyAppts() {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        try (SqlSession session = MyBatisUtil.openSession()) {
            AppointmentMapper am = session.getMapper(AppointmentMapper.class);
            UserMapper um = session.getMapper(UserMapper.class);
            List<Appointment> list;
            if ("EMPLOYEE".equalsIgnoreCase(loggedUserRole)) list = am.listByEmployee(loggedUserId);
            else list = am.listByUser(loggedUserId);

            out.println("OK COUNT " + list.size());
            for (Appointment a : list) {
                String otherName;
                if ("EMPLOYEE".equalsIgnoreCase(loggedUserRole)) otherName = um.usernameById(a.getUserId());
                else otherName = um.usernameById(a.getEmployeeId());
                out.println("APPT " + a.getId() + "|" + otherName + "|" + a.getDate() + "|" + a.getStartTime() + "|" + a.getStatus());
            }
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR ApptsFailed");
        }
    }

    private void handleConfirm(String payload) {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        if (!"EMPLOYEE".equalsIgnoreCase(loggedUserRole)) {
            out.println("ERROR PermissionDenied");
            return;
        }
        try (SqlSession session = MyBatisUtil.openSession()) {
            AppointmentMapper am = session.getMapper(AppointmentMapper.class);
            Map<String, Object> params = new HashMap<>();
            params.put("id", Integer.parseInt(payload));
            params.put("status", "CONFIRMED");
            am.updateStatus(params);
            out.println("OK Confirmed");
        } catch (Exception e) {
            out.println("ERROR ConfirmFailed");
        }
    }

    private void handleMyInfo() {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        try (SqlSession session = MyBatisUtil.openSession()) {
            UserMapper um = session.getMapper(UserMapper.class);
            User u = um.findByUsername(um.usernameById(loggedUserId));
            out.println("OK " + u.getUsername() + " " + u.getRole());
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR GetInfoFailed");
        }
    }
}