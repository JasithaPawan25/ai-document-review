package com.jasitha.docreview.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;
import java.util.stream.Collectors;

/** The logged-in user, in the shape the service layer needs. */
public record CurrentUser(String username, Set<String> roles) {

    public static CurrentUser from(UserDetails user) {
        return new CurrentUser(user.getUsername(), user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet()));
    }

    public boolean canReview() {
        return roles.contains("ROLE_REVIEWER") || roles.contains("ROLE_ADMIN");
    }
}
