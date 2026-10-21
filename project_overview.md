# SKIPPS QCE – Project Overview

---

## 1. Project Summary
**SKIPPS QCE (Quick Connect Ease / Secure Server)** is an Android application that serves as a remote‑control client for a Linux/Unix server. It enables users to manage a remote machine from their phone: establish an SSH session, execute shell commands, transfer files, and control power state (wake‑on‑LAN, suspend). The app combines a clean Material UI with background networking tasks to provide a lightweight dashboard for server administration.

---

## 2. End‑to‑End User Flow
```mermaid
flowchart TD
    A[Splash Screen] --> B{First launch?}
    B -->|Yes| C[SetPasswordActivity]
    B -->|No| D[LoginActivity]
    C --> D
    D --> E[MainActivity (Navigation Drawer)]
    E --> F[Power Management]
    E --> G[Upload File]
    E --> H[SSH Command]
    E --> I[Download File]
    E --> J[HTTP Browser]
    F --> K[Wake‑on‑LAN / Sleep]
    H --> L[Enter command → Execute → Show output]
    G --> M[Select local file → Upload via SFTP]
    I --> N[Browse remote dir → Download → Save locally]
    J --> O[Browse web‑server dir → Open in Notepad]
    O --> P[Edit → Upload back]
    E --> Q[Logout]
```
1. **Splash** – shows animation, then decides whether to set an app password.
2. **SetPassword** – creates a local password (8+ chars, letter, digit, special).
3. **Login** – password entry or fingerprint authentication.
4. **Main** – navigation drawer with five core sections.
5. **Power** – wake‑on‑LAN packet or `systemctl suspend` via SSH.
6. **SSH** – free‑form command input, remote execution, output displayed.
7. **Upload / Download** – SFTP file transfer using JSch.
8. **HTTP** – list web‑server files, edit with built‑in Notepad activity.
9. **Logout** – ends session.

---

## 3. Technology Stack
| Layer | Technology | Reason |
|-------|------------|--------|
| **Platform** | Android (Java) | Native mobile UI, easy access to system services |
| **UI** | AndroidX AppCompat, Material Components, Navigation Component, ViewBinding, Lottie | Consistent Material Design, smooth animations |
| **Biometrics** | Android Biometric library | Fingerprint login for convenience |
| **SSH / SFTP** | **JSch** library | Mature Java SSH implementation, supports password auth and port forwarding |
| **Persistence** | **SharedPreferences** | Simple key‑value store for connection settings and password hash |
| **Networking** | AsyncTask (background threads) | Keeps UI responsive while performing network I/O |
| **Wake‑on‑LAN** | Custom `MagicPacket` & `WOLManager` | Sends UDP magic packet on port 9 |
| **Permissions** | INTERNET, MANAGE_EXTERNAL_STORAGE, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE, ACCESS_NOTIFICATION_POLICY | Required for network and file operations |

---

## 4. Core Features & Corresponding Classes
| Feature | Primary Classes / Fragments | Brief Description |
|---------|----------------------------|-------------------|
| **Authentication** | `SetPasswordActivity`, `LoginActivity`, `SharedPreferenceManager` | Password creation, validation, fingerprint support, stored in SharedPreferences |
| **SSH Session** | `SSHManager`, `SSHInitTask`, `SSHFragment` | Manages a singleton JSch `Session`, connects on login/config save, executes commands |
| **Power Management** | `Power_Mgmt_Fragment`, `SleepTask`, `WOLTask`, `MagicPacket` | Sends suspend command via SSH, wakes server via UDP magic packet |
| **File Upload** | `Upload_File_Fragment`, `UploadTask` | Android file picker → SFTP `put` to remote path |
| **File Download** | `Download_File_Fragment`, `DownloadTask` | Lists remote files → SFTP `get` → Android save dialog |
| **HTTP Browser** | `HTTP_Fragment`, `HTTPDownloadTask`, `HTTPUploadTask` | Lists `/var/www/html/` files, opens in `NotepadActivity` for edit/upload |
| **Notepad Editor** | `NotepadActivity` | Simple text editor for remote files fetched via HTTP |
| **Utility** | `SharedPreferenceManager`, `ListTask` | Centralised settings handling and remote directory listing |

---

## 5. How It Works (High‑Level Architecture)
1. **Entry Point** – `SplashActivity` decides whether to launch `SetPasswordActivity` (first‑run) or `LoginActivity`.
2. **Authentication Flow** – Password is stored as a hash in `SharedPreferences`. Fingerprint uses Android’s `BiometricPrompt`.
3. **Configuration** – `ConfigActivity` collects IP, MAC, SSH port, username, password, remote path; values saved via `SharedPreferenceManager`.
4. **SSH Initialization** – `SSHInitTask` reads the saved settings, creates a JSch `Session`, and connects. The session is stored in the singleton `SSHManager`.
5. **Feature Fragments** – Each drawer item loads a fragment that interacts with the shared SSH session or SFTP channel.
6. **Background Execution** – All network I/O runs in `AsyncTask` subclasses (`SSHInitTask`, `UploadTask`, `DownloadTask`, `SleepTask`, `WOLTask`). UI updates are posted on the main thread.
7. **Power Control** – `SleepTask` runs `sudo -S -p '' systemctl suspend` over SSH; `WOLTask` builds a magic packet from the MAC address and sends it via UDP.
8. **File Operations** – `UploadTask`/`DownloadTask` use JSch’s `ChannelSftp` to transfer files; progress is shown via Toasts.
9. **HTTP Editing** – `HTTPFragment` lists files under `/var/www/html/`. Selecting a file launches `NotepadActivity`, which downloads via `HTTPDownloadTask`, edits, and uploads via `HTTPUploadTask`.

---

## 6. Sample Interview Questions
### Architecture & Design
1. **Why did you choose a singleton `SSHManager` instead of creating a new session for each request?**
2. **Explain how you keep the UI responsive while performing long‑running network operations.**        
3. **What are the trade‑offs of storing the server password in `SharedPreferences`?**
4. **How would you refactor the app to support multiple concurrent SSH sessions?**
5. **Describe how the navigation drawer and fragment architecture simplify code reuse.**

### Technical Details
1. **How does JSch handle host‑key verification, and why did you disable strict checking?**
2. **What is the purpose of `MagicPacket` and how is it constructed?**
3. **Explain the flow of a command entered in `SSHFragment` from UI to remote execution.**
4. **How does the app ensure that file uploads/downloads do not block the main thread?**
5. **What would you change to make the password storage more secure?**

### Security & Reliability
1. **Identify potential security risks in the current implementation and propose mitigations.**
2. **How would you add certificate‑based SSH authentication?**
3. **What happens if the SSH session drops while a command is running?**
4. **How can you protect the app against man‑in‑the‑middle attacks on the SFTP channel?**
5. **Describe a strategy for handling network connectivity loss gracefully.**

---

## 7. Future Enhancements
- Replace password‑hash storage with Android Keystore‑backed encryption.
- Introduce RSA key‑pair authentication for SSH.
- Migrate background tasks to **WorkManager** for better lifecycle handling.
- Add a **command history** UI with reusable snippets.
- Implement **real‑time server status monitoring** (CPU, memory) via a lightweight REST endpoint.
- Provide a **dark theme** toggle using Material Theming.

---

*Generated on 2026‑10‑21.*
