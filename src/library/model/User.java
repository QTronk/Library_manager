package library.model;

import java.io.Serializable;

public class User implements Serializable {
    private String username;
    private String fullName;
    private String role; // "ADMIN", "LIBRARIAN", "READER"

    public User() {}

    public User(String username, String fullName, String role) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}