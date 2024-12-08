import java.io.*;
import java.net.*;
import java.util.*;

public class InstantMessengerServer {
    private static final int PORT = 12345;
    private static Map<String, String> userDatabase = new HashMap<>(); // Simulated database
    private static Map<String, Socket> activeClients = new HashMap<>();  // Store active clients by username (Socket)

    public static void main(String[] args) {
        System.out.println("Server starting...");
        // Add some sample users (username:password)
        userDatabase.put("user1", "password1");
        userDatabase.put("user2", "password2");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();  // Accept client connections
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private String username;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Authenticate user
                out.println("Enter username:");
                username = in.readLine();
                out.println("Enter password:");
                String password = in.readLine();

                if (authenticate(username, password)) {
                    out.println("Authentication successful!");
                    synchronized (activeClients) {
                        activeClients.put(username, socket);  // Store the Socket, not PrintWriter
                        System.out.println(username + " has logged in. Active clients: " + activeClients.keySet());
                        broadcastStatus(username + " is online!");
                    }

                    // Communication loop
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("SEND")) {
                            String recipient = message.split(" ")[1];
                            String text = message.split(" ", 3)[2];
                            sendMessage(recipient, text);
                        } else if (message.startsWith("FILE")) {
                            String[] parts = message.split(" ");
                            String recipient = parts[1];
                            String filename = parts[2];
                            sendFile(recipient, filename);
                        }
                    }
                } else {
                    out.println("Authentication failed. Goodbye!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (activeClients) {
                    if (username != null) {
                        activeClients.remove(username);  // Remove from active clients when disconnected
                        System.out.println(username + " has disconnected. Active clients: " + activeClients.keySet());
                        broadcastStatus(username + " is offline.");
                    }
                }
            }
        }

        // Authenticate user credentials
        private boolean authenticate(String username, String password) {
            System.out.println("Authenticating: " + username + " with password: " + password);
            return userDatabase.containsKey(username) && userDatabase.get(username).equals(password);
        }

        // Send a message to a recipient
        private void sendMessage(String recipient, String text) {
            synchronized (activeClients) {
                Socket recipientSocket = activeClients.get(recipient);
                if (recipientSocket != null) {
                    try {
                        PrintWriter recipientOut = new PrintWriter(recipientSocket.getOutputStream(), true);
                        recipientOut.println("Message from " + username + ": " + text);
                    } catch (IOException e) {
                        out.println("Error sending message to " + recipient + ": " + e.getMessage());
                    }
                } else {
                    out.println("User " + recipient + " is not online.");
                }
            }
        }

        // Send a file to a recipient
        private void sendFile(String recipient, String filename) {
            synchronized (activeClients) {
                System.out.println("Attempting file transfer. Active clients: " + activeClients.keySet());
                Socket recipientSocket = activeClients.get(recipient);

                if (recipientSocket != null) {
                    try {
                        // Verify the file exists before sending
                        File file = new File(filename);
                        if (!file.exists()) {
                            System.out.println("Error: File not found - " + filename);
                            out.println("Error: File not found.");
                            return;
                        }

                        // Notify recipient about the incoming file
                        PrintWriter recipientOut = new PrintWriter(recipientSocket.getOutputStream(), true);
                        recipientOut.println("FILE_FROM " + username + " " + filename);
                        System.out.println("File transfer started for file: " + filename);

                        // Send the file data
                        BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(file));
                        OutputStream recipientSocketOut = recipientSocket.getOutputStream();

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fileInput.read(buffer)) > 0) {
                            recipientSocketOut.write(buffer, 0, bytesRead);
                        }
                        fileInput.close();
                        recipientSocketOut.flush();

                        out.println("File sent successfully to " + recipient);
                        System.out.println("File sent successfully.");
                    } catch (IOException e) {
                        System.out.println("Error during file transfer: " + e.getMessage());
                        out.println("Error sending file: " + e.getMessage());
                    }
                } else {
                    System.out.println("Recipient " + recipient + " is not online.");
                    out.println("User " + recipient + " is not online.");
                }
            }
        }

        // Broadcast status message to all connected clients
        private void broadcastStatus(String status) {
            synchronized (activeClients) {
                System.out.println("Broadcasting status to active clients: " + activeClients.keySet());
                for (Socket clientSocket : activeClients.values()) {
                    try {
                        PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                        clientOut.println("STATUS: " + status);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
