package app.client;

import java.io.*;
import java.net.Socket;

public class ServerConnection {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Read welcome
            in.readLine();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void disconnect() {
        try { if(socket != null) socket.close(); } catch(IOException e){}
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void send(String msg) {
        if(out != null) out.println(msg);
    }

    public String readResponse() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if ("PONG".equalsIgnoreCase(line.trim())) continue;
            return line;
        }
        return null;
    }
}