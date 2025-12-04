package app.server;

import app.common.models.User;
import app.server.mappers.UserMapper;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.io.InputStream;
import java.io.IOException;

/**
 * Main server: starts socket listener, initializes DB schema (schema.sql),
 * seeds admin and employee if not present, and accepts client connections.
 */
public class MainServer {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final String dbUrl = "jdbc:sqlite:appointments.db";

    public MainServer(int port) {
        this.port = port;
        initDatabase();
    }

    private void initDatabase() {
        // run schema.sql
        try (Connection c = DriverManager.getConnection(dbUrl)) {
            c.setAutoCommit(true);
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
                if (in != null) {
                    String sql = new String(in.readAllBytes());
                    try (Statement st = c.createStatement()) {
                        st.executeUpdate("PRAGMA foreign_keys = ON;");
                        // split by semicolon and execute
                        for (String stmt : sql.split(";")) {
                            String t = stmt.trim();
                            if (!t.isEmpty()) st.executeUpdate(t);
                        }
                    }
                }
            }
            // seed admin & employee if not exists using MyBatis
            try (var session = MyBatisUtil.openSession()) {
                var um = session.getMapper(UserMapper.class);
                User u = um.findByUsername("admin");
                if (u == null) {
                    String salt = Utils.randomSaltBase64(16);
                    String hash = Utils.hashPassword("admin123", salt);
                    User admin = new User();
                    admin.setUsername("admin"); admin.setSalt(salt); admin.setHash(hash); admin.setRole("ADMIN");
                    um.insertUser(admin);
                }
                User e = um.findByUsername("employee1");
                if (e == null) {
                    String salt = Utils.randomSaltBase64(16);
                    String hash = Utils.hashPassword("emp123", salt);
                    User emp = new User();
                    emp.setUsername("employee1"); emp.setSalt(salt); emp.setHash(hash); emp.setRole("EMPLOYEE");
                    um.insertUser(emp);
                }
            } catch (Exception ex) {
                System.err.println("Seeding users failed: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("DB init error: " + ex.getMessage());
        }
    }

    public void start() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                Socket s = ss.accept();
                System.out.println("Accepted " + s.getRemoteSocketAddress());
                pool.submit(new ClientHandler(s));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 5555;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        MainServer server = new MainServer(port);
        server.start();
    }
}
