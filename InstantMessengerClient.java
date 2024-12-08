import java.io.*;
import java.net.*;
import java.util.Scanner;

public class InstantMessengerClient {
    private static final String SERVER_ADDRESS = "127.0.0.1"; // Server IP (localhost)
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            Scanner scanner = new Scanner(System.in);

            // Authentication
            System.out.println(in.readLine()); // "Enter username:"
            String username = scanner.nextLine();
            out.println(username);

            System.out.println(in.readLine()); // "Enter password:"
            String password = scanner.nextLine();
            out.println(password);

            String response = in.readLine(); // "Authentication successful!" or "failed"
            System.out.println(response);

            if (!response.contains("successful")) {
                return; // Exit if authentication fails
            }

            // Start a thread to handle incoming messages
            Thread listenerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        if (serverMessage.startsWith("FILE_FROM")) {
                            String[] parts = serverMessage.split(" ");
                            String sender = parts[1];
                            String filename = parts[2];

                            System.out.println("Incoming file from " + sender + ": " + filename);

                            // Save the file in the current directory with the same filename (remove full path)
                            String saveAsFilename = "received_" + filename.substring(filename.lastIndexOf("/") + 1); // Remove path
                            try (BufferedOutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(saveAsFilename));
                                 InputStream serverIn = socket.getInputStream()) {

                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = serverIn.read(buffer)) > 0) {
                                    fileOutput.write(buffer, 0, bytesRead);
                                }
                                fileOutput.flush();
                                System.out.println("File received and saved as: " + saveAsFilename);
                            } catch (IOException e) {
                                System.out.println("Error receiving file: " + e.getMessage());
                            }
                        } else {
                            System.out.println(serverMessage);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            listenerThread.start();

            // Main loop for sending messages
            System.out.println("Type your messages (or type 'exit' to quit):");
            String message;
            while (!(message = scanner.nextLine()).equalsIgnoreCase("exit")) {
                if (message.startsWith("SEND") || message.startsWith("FILE")) {
                    System.out.println("Sending command: " + message); // Debug: Command sent
                    out.println(message);
                } else {
                    System.out.println("Invalid command. Use 'SEND <recipient> <message>' or 'FILE <recipient> <filename>' to communicate.");
                }
            }

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }
}
