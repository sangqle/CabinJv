package com.cabin.express.zdemo.dto;

public class UserInfo {
    private Long userId;
    private String userName;
    private String email;
    private String phone;

    @Override
    public String toString() {
        return String.format("User ID: %d, User Name: %s, Email: %s, Phone: %s", userId, userName, email, phone);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
