package com.groupstp.googleoauth.service;

import com.groupstp.googleoauth.data.GoogleUserData;
import com.haulmont.cuba.security.entity.User;

import javax.annotation.Nullable;

/**
 * Google authentication based system users registration service
 */
public interface SocialRegistrationService {
    
    String NAME = "googleoauth_SocialRegistrationService";

    /**
     * Find user in system by provided google authentication data, or a register a new one if possible
     *
     * @param data Google authentication data.
     * @return system user or null if user not found and creation is impossible.
     */
    @Nullable
    User findUser(GoogleUserData data);
}