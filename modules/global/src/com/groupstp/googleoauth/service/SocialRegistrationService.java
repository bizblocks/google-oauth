package com.groupstp.googleoauth.service;

import com.haulmont.cuba.security.entity.User;

import javax.annotation.Nullable;

public interface SocialRegistrationService {
    String NAME = "googleoauth_SocialRegistrationService";

    @Nullable
    User findUser(String email);
}