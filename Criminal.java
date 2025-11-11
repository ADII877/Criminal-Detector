    /*
    * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
    * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
    */

    package com.criminaldetector.model;

    import jakarta.persistence.*;
    import jakarta.validation.constraints.*;
    import lombok.*;

    @Entity
    @Table(name = "criminals")
    @Data
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class Criminal {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @NotBlank(message = "Name is required")
        @Column(nullable = false)
        private String name;
        
        @NotBlank(message = "Crime details is required")
        @Column(columnDefinition = "TEXT", nullable = false)
        private String crimeDetails;
        
        @Column(name = "image", nullable = false)
        @NotBlank(message = "Image name is required")
        private String imageName;

        @Min(value = 0, message = "Age must be greater than or equal to 0")
        @Max(value = 150, message = "Age must be less than or equal to 150")
        @Column(nullable = false)
        private int age;

        @NotBlank(message = "Gender is required")
        @Column(nullable = false)
        private String gender;

        @Column(name = "created_at", nullable = false, updatable = false)
        private java.time.LocalDateTime createdAt;

        // @Column(name = "updated_at")
        // private java.time.LocalDateTime updatedAt;

        @PrePersist
        protected void onCreate() {
            createdAt = java.time.LocalDateTime.now();
        }

        // @PreUpdate
        // protected void onUpdate() {
        //     updatedAt = java.time.LocalDateTime.now();
        // }
    }
