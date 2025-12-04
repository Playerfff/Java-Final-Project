PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     username TEXT UNIQUE NOT NULL,
     hash TEXT NOT NULL,
     salt TEXT NOT NULL,
     role TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS appointments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    employee_id INTEGER NOT NULL,
    date TEXT NOT NULL,         -- YYYY-MM-DD
    start_time TEXT NOT NULL,   -- HH:mm
    end_time TEXT NOT NULL,     -- HH:mm
    status TEXT NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id),
FOREIGN KEY(employee_id) REFERENCES users(id)
);
