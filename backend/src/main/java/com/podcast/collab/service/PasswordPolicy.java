package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    public void validate(String password) {
        if (password == null || password.length() < 10) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, "Password must be at least 10 characters");
        }
        if (password.length() > 100) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, "Password is too long");
        }
        if (!LETTER.matcher(password).find()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, "Password must contain a letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, "Password must contain a digit");
        }
        if (!SPECIAL.matcher(password).find()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, "Password must contain a special character");
        }
    }
}
