package com.groupstp.googleoauth.service;

import com.groupstp.googleoauth.config.GoogleConfig;
import com.groupstp.googleoauth.data.GoogleUserData;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.security.entity.Group;
import com.haulmont.cuba.security.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@Service(SocialRegistrationService.NAME)
public class SocialRegistrationServiceBean implements SocialRegistrationService {

    @Inject
    protected Persistence persistence;
    @Inject
    protected Metadata metadata;

    @Inject
    protected GoogleConfig config;

    @Override
    @Transactional
    @Nullable
    public User findUser(GoogleUserData data) {
        EntityManager em = persistence.getEntityManager();

        // Find existing user
        TypedQuery<User> query = em.createQuery(
                "select u from sec$User u where u.email = :email and (u.active = true or u.active is null)",
                User.class);
        query.setParameter("email", data.getEmail() == null ? StringUtils.EMPTY : data.getEmail().toLowerCase());
        query.setViewName(View.LOCAL);

        User existingUser = query.getFirstResult();
        if (existingUser != null) {
            return existingUser;
        }

        String domains = config.getAcceptDomains();
        if (!StringUtils.isEmpty(domains)) {
            return createUserByDomain(domains, data);
        }

        return null;
    }

    @Nullable
    protected User createUserByDomain(String acceptDomains, GoogleUserData data) {
        List<String> domains = Arrays.asList(acceptDomains.split(","));
        boolean create = domains.contains(data.getDomain());
        if (!create) {
            String[] parts = data.getEmail().split("@");
            String currentEmailDomain = parts[parts.length - 1];

            create = domains.contains(currentEmailDomain);
        }
        if (create) {
            return createUser(data);
        }
        return null;
    }

    protected User createUser(GoogleUserData data) {
        EntityManager em = persistence.getEntityManager();

        User user = metadata.create(User.class);
        user.setGroup(getDefaultGroup());
        user.setActive(true);
        user.setLogin(data.getEmail());
        user.setEmail(data.getEmail());
        user.setName(data.getName());
        user.setFirstName(data.getFirstName());
        user.setMiddleName(data.getMiddleName());
        user.setLastName(data.getLastName());

        em.persist(user);

        return user;
    }

    protected Group getDefaultGroup() {
        EntityManager em = persistence.getEntityManager();
        return em.find(Group.class, config.getDefaultGroupId(), View.MINIMAL);
    }
}

