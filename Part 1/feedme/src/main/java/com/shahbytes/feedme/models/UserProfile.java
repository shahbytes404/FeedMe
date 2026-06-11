package com.shahbytes.feedme.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserProfile {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String bio;

    @Column(nullable = false)
    private boolean hotUser;

    public UserProfile() {
    }

    public UserProfile(String id, String handle, String name, String bio, boolean hotUser) {
        this.id = id;
        this.handle = handle;
        this.name = name;
        this.bio = bio;
        this.hotUser = hotUser;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isHotUser() {
        return hotUser;
    }

    public void setHotUser(boolean hotUser) {
        this.hotUser = hotUser;
    }
}
