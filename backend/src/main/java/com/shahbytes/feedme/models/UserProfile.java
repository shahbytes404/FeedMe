package com.shahbytes.feedme.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
