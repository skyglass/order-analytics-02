package net.greeta.order.config.auth;

public interface UserCredentialsProvider {

    public String getUsername();

    public String getPassword();
}
