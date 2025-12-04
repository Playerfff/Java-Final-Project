### Run Instructions

1. Run run_server script.

2. Run run_client script.

### Note on Containerization

I considered containerizing the application as suggested. However, since the client uses Java Swing (GUI), running it
inside a Docker container requires complex X11 display configurations on the host machine, which complicates the "
out-of-box" experience.

Therefore, I have packaged the application as a standalone Fat JAR that includes all dependencies (SQLite, MyBatis,
drivers). It requires only Java1.8 to be installed, ensuring it runs instantly on your machine without setup.

### Special Accounts

- Admin: username: admin, password: admin123
- Default Employee: username: employee1, password: emp123