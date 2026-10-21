package com.example.secureserver.ui.SSH;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.SKIPPS.utils.ssh.SSHManager;
import com.example.secureserver.R;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SSHFragment extends Fragment {

    private EditText commandInputEditText;
    private TextView outputTextView;
    private Button sendCommandButton;
    private ScrollView scrollView;

    // Persistent shell channel — stays open across commands so cd, env vars, etc. all work
    private Channel shellChannel;
    private InputStream shellInputStream;
    private OutputStream shellOutputStream;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Thread readerThread;
    private final StringBuilder terminalOutput = new StringBuilder();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_ssh, container, false);

        commandInputEditText = root.findViewById(R.id.edittext_command_input);
        outputTextView = root.findViewById(R.id.textview_output);
        sendCommandButton = root.findViewById(R.id.button_send_command);
        scrollView = (ScrollView) outputTextView.getParent();

        sendCommandButton.setOnClickListener(v -> {
            String command = commandInputEditText.getText().toString().trim();
            if (!command.isEmpty()) {
                sendCommand(command);
                commandInputEditText.setText("");
            } else {
                Toast.makeText(getContext(), "Please enter a command", Toast.LENGTH_SHORT).show();
            }
        });

        // Open the persistent shell as soon as the fragment is shown
        openShell();

        return root;
    }

    /**
     * Opens a persistent PTY shell channel on the SSH session.
     * This runs once — all subsequent commands are sent through the same shell,
     * so stateful commands like "cd", setting env variables, etc. all work correctly.
     */
    private void openShell() {
        new Thread(() -> {
            try {
                Session session = SSHManager.getSession();
                if (session == null || !session.isConnected()) {
                    appendOutput("Not connected to server.\nGo to Settings and save your server details first.\n");
                    return;
                }

                shellChannel = session.openChannel("shell");

                // Use dumb terminal — disables color codes, cursor sequences, and
                // all the control characters that show as garbage in a plain TextView
                ((com.jcraft.jsch.ChannelShell) shellChannel).setPtyType("dumb");
                ((com.jcraft.jsch.ChannelShell) shellChannel).setPty(true);

                shellInputStream = shellChannel.getInputStream();
                shellOutputStream = shellChannel.getOutputStream();

                shellChannel.connect();

                appendOutput("Shell connected. You can now run commands.\n\n");

                // Disable the shell prompt so it doesn't clutter the output.
                // PS1='' removes the prompt, PS2='' removes continuation prompt.
                // TERM=dumb tells apps not to emit color/control sequences.
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // wait for shell to be ready
                        shellOutputStream.write("export PS1='' PS2='' TERM=dumb\n".getBytes());
                        shellOutputStream.flush();
                    } catch (Exception ignored) {}
                }).start();

                // Start a background reader thread that continuously reads
                // output from the shell and appends it to the terminal view
                startReaderThread();

            } catch (JSchException | IOException e) {
                appendOutput("Failed to open shell: " + e.getMessage() + "\n");
            }
        }).start();
    }

    /**
     * Continuously reads output from the shell stream and posts it to the UI.
     * Runs on a dedicated background thread for the lifetime of the fragment.
     */
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int bytesRead;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Block until data is available, then read it
                    if (shellInputStream.available() > 0) {
                        bytesRead = shellInputStream.read(buffer);
                        if (bytesRead > 0) {
                            String output = stripAnsi(new String(buffer, 0, bytesRead));
                            appendOutput(output);
                        }
                    } else if (shellChannel == null || shellChannel.isClosed()) {
                        appendOutput("\nShell session ended.\n");
                        break;
                    } else {
                        // Small sleep to avoid busy-waiting when no data is available
                        Thread.sleep(50);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Fragment was destroyed or shell closed — exit quietly
                Thread.currentThread().interrupt();
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Sends a command to the persistent shell exactly as if you typed it in a terminal.
     * The newline (\n) acts as pressing Enter.
     */
    private void sendCommand(String command) {
        if (shellOutputStream == null || shellChannel == null || shellChannel.isClosed()) {
            // Shell closed unexpectedly — try to reopen it
            appendOutput("\nShell not connected. Reconnecting...\n");
            openShell();
            return;
        }

        // Show the typed command in the output (like a real terminal)
        appendOutput("$ " + command + "\n");

        new Thread(() -> {
            try {
                shellOutputStream.write((command + "\n").getBytes());
                shellOutputStream.flush();
            } catch (IOException e) {
                appendOutput("Error sending command: " + e.getMessage() + "\n");
            }
        }).start();
    }

    /**
     * Appends text to the terminal output TextView and auto-scrolls to the bottom.
     */
    private void appendOutput(String text) {
        handler.post(() -> {
            terminalOutput.append(text);
            outputTextView.setText(terminalOutput.toString());
            // Auto-scroll to bottom so latest output is always visible
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    /**
     * Strips ANSI escape codes, OSC sequences, terminal control characters,
     * and anything else that shows as garbage in a plain TextView.
     * Keeps only printable text, newlines, and tabs.
     */
    private String stripAnsi(String input) {
        if (input == null) return "";

        return input
                .replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "")    // CSI sequences (colors, cursor, erase)
                .replaceAll("\u001B\\][^\u0007\u001B]*[\u0007\u001B\\\\]", "") // OSC sequences (the long garbage strings you saw)
                .replaceAll("\u001B[^\\[\\]]", "")                 // other 2-char ESC sequences
                .replaceAll("\u001B", "")                          // any remaining lone ESC
                .replaceAll("\r\n", "\n")                          // normalize Windows CRLF
                .replaceAll("\r", "\n")                            // lone CR to LF
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // strip non-printable control chars, keep \t and \n
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up the reader thread and shell channel when leaving the fragment
        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (shellChannel != null) {
            shellChannel.disconnect();
        }
        try {
            if (shellInputStream != null) shellInputStream.close();
            if (shellOutputStream != null) shellOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Clear terminal history for next time
        terminalOutput.setLength(0);
    }
}
