package com.groupstp.googleoauth.service;

// import com.groupstp.googleoauth.entity.SocialUser;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.security.entity.User;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.transaction.Transactional;

@Service(SocialRegistrationService.NAME)
public class SocialRegistrationServiceBean implements SocialRegistrationService {

    @Inject
    private Persistence persistence;

    @Override
    @Transactional
    @Nullable
    public User findUser(String email) {
        EntityManager em = persistence.getEntityManager();

        // Find existing user
        TypedQuery<User> query = em.createQuery(
                "select u from sec$User u where u.email like :email",
                User.class);
        query.setParameter("email", email);
        query.setViewName(View.LOCAL);

        User existingUser = query.getFirstResult();
        if (existingUser != null) {
            return existingUser;
        }

        return null;
    }
}

