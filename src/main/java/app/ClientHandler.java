package app;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.mappers.AppointmentMapper;
import app.models.Appointment;
import app.models.User;

/**
 * Handles a single client connection. Uses MyBatis mappers for DB operations.
 * Commands:
 * REGISTER username|password|role
 * LOGIN username|password
 * LIST_EMPLOYEES
 * BOOK employeeId|datetime
 * MY_APPTS
 * PING  -> respond PONG
 * QUIT
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Integer loggedUserId = null;

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
                    out.println("PONG");
                    continue;
                }
                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase();
                String payload = parts.length > 1 ? parts[1] : "";

                switch (cmd) {
                    case "REGISTER":
                        handleRegister(payload);
                        break;
                    case "LOGIN":
                        handleLogin(payload);
                        break;
                    case "LIST_EMPLOYEES":
                        handleListEmployees();
                        break;
                    case "BOOK":
                        handleBook(payload);
                        break;
                    case "USERS_APPTS":
                        handleUsersAppts();
                        break;
                    case "EMPLOYEES_APPTS":
                        handleEmployeesAppts();
                        break;
                    case "MY_INFO":
                        handleMyInfo();
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
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            String[] p = payload.split("\\|");
            if (p.length < 2) {
                out.println("ERROR BadPayload");
                return;
            }
            String username = p[0];
            String password = p[1];
            String role = p.length >= 3 ? p[2] : "USER";
            User existing = um.findByUsername(username);
            if (existing != null) {
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
            // commit done by MyBatis auto-commit session
            out.println("OK Registered");
        } catch (Exception e) {
            out.println("ERROR RegisterFailed");
        }
    }

    private void handleLogin(String payload) {
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
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
                out.println("OK " + loggedUserId);
            } else out.println("ERROR AuthFailed");
        } catch (Exception e) {
            out.println("ERROR AuthError");
        }
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

        int empId = Integer.parseInt(p[0]);
        String date = p[1];
        String start = p[2];
        String end = p[3];

        try (var session = MyBatisUtil.openSession()) {
            var am = session.getMapper(AppointmentMapper.class);

            Map<String, Object> params = new HashMap<>();
            params.put("employeeId", empId);
            params.put("date", date);
            params.put("startTime", start);
            params.put("endTime", end);

            Integer conflict = am.findConflict(params);
            if (conflict != null) {
                out.println("ERROR SlotTaken");
                return;
            }

            Appointment a = new Appointment();
            a.setUserId(loggedUserId);
            a.setEmployeeId(empId);
            a.setDate(date);
            a.setStartTime(start);
            a.setEndTime(end);
            a.setStatus("CONFIRMED");

            am.insertAppointment(a);
            out.println("OK Booked");
        }
    }

    private void handleUsersAppts() {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        try (var session = MyBatisUtil.openSession()) {
            var am = session.getMapper(app.mappers.AppointmentMapper.class);
            var um = session.getMapper(app.mappers.UserMapper.class);
            List<Appointment> list = am.listByUser(loggedUserId);
            out.println("OK COUNT " + list.size());
            for (Appointment a : list) {
                String empName = um.usernameById(a.getEmployeeId());
                out.println("APPT " + a.getId() + "|" + empName + "|" + a.getDate() + "|" + a.getStartTime() + "|" + a.getEndTime() + "|" + a.getStatus());
            }
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR ApptsFailed");
        }
    }

    private void handleEmployeesAppts() {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        try (var session = MyBatisUtil.openSession()) {
            var am = session.getMapper(app.mappers.AppointmentMapper.class);
            var um = session.getMapper(app.mappers.UserMapper.class);
            List<Appointment> list = am.listByEmployee(loggedUserId);
            out.println("OK COUNT " + list.size());
            for (Appointment a : list) {
                String customerName = um.usernameById(a.getUserId());
                out.println("APPT " + a.getId() + "|" + customerName + "|" + a.getDate() + "|" + a.getStartTime() + "|" + a.getEndTime() + "|" + a.getStatus());
            }
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR ApptsFailed");
        }
    }

    private void handleMyInfo() {
        if (loggedUserId == null) {
            out.println("ERROR NotLoggedIn");
            return;
        }
        try (var session = MyBatisUtil.openSession()) {
            var um = session.getMapper(app.mappers.UserMapper.class);
            User u = um.findByUsername(um.usernameById(loggedUserId));
            out.println("OK " + u);
            out.println("END");
        } catch (Exception e) {
            out.println("ERROR GetInfoFailed");
        }
    }
}
