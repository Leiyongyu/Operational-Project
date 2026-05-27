package com.asinking.com.openapi.dto.response;

public class UserLoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresAtMillis;
    private String account;
    private String role;
    private String ownerName;

    public UserLoginResponse() {
    }

    public UserLoginResponse(String token, long expiresAtMillis, String account, String role, String ownerName) {
        this.token = token;
        this.expiresAtMillis = expiresAtMillis;
        this.account = account;
        this.role = role;
        this.ownerName = ownerName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
}

