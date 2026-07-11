package io.taskflow.service.auth;

import io.taskflow.domain.Organization;
import io.taskflow.domain.OrganizationMembership;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.OrganizationRole;
import io.taskflow.dto.auth.AuthResponse;
import io.taskflow.dto.auth.LoginRequest;
import io.taskflow.dto.auth.OrganizationSummary;
import io.taskflow.dto.auth.RegisterRequest;
import io.taskflow.dto.auth.UserSummary;
import io.taskflow.exception.ConflictException;
import io.taskflow.exception.ForbiddenException;
import io.taskflow.exception.UnauthorizedException;
import io.taskflow.repository.OrganizationMembershipRepository;
import io.taskflow.repository.OrganizationRepository;
import io.taskflow.repository.UserRepository;
import io.taskflow.security.JwtService;
import io.taskflow.security.JwtService.IssuedAccessToken;
import io.taskflow.security.RefreshTokenService;
import io.taskflow.security.RefreshTokenService.IssuedRefreshToken;
import io.taskflow.security.RefreshTokenService.RotationResult;
import io.taskflow.service.cache.MembershipCache;
import io.taskflow.service.cache.MembershipView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Concrete auth flows. Each public method is one logical transaction.
 *
 * <p>The membership lookup runs on every login/refresh/switch-org; it's cached in
 * Redis ({@link MembershipCache}) with precise eviction on member-table mutations.
 * Register evicts after committing the new membership so the first refresh sees it.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final MembershipCache membershipCache;

    // ------------------------------------------------------------------ register

    @Override
    @Transactional
    public Result register(RegisterRequest req, HttpServletRequest httpRequest) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (organizations.existsBySlug(req.organizationSlug().toLowerCase())) {
            throw new ConflictException("SLUG_TAKEN", "Organization slug is already in use");
        }

        User user = users.save(User.builder()
                .email(req.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .enabled(true)
                .lastLoginAt(Instant.now())
                .build());

        Organization org = organizations.save(Organization.builder()
                .name(req.organizationName())
                .slug(req.organizationSlug().toLowerCase())
                .build());

        OrganizationMembership membership = memberships.save(OrganizationMembership.builder()
                .user(user)
                .organization(org)
                .role(OrganizationRole.ADMIN)
                .build());

        // Brand-new user: no cache entry to evict. Construct the view inline.
        MembershipView active = new MembershipView(
                user.getId(), org.getId(), org.getName(), org.getSlug(), OrganizationRole.ADMIN);

        IssuedAccessToken access = jwtService.issueAccessToken(
                user.getId(), org.getId(), OrganizationRole.ADMIN);
        IssuedRefreshToken refresh = refreshTokens.issueNewSession(user, httpRequest);

        AuthResponse body = buildResponse(access, user, active, List.of(active));
        return new Result(body, refresh);
    }

    // --------------------------------------------------------------------- login

    @Override
    @Transactional
    public Result login(LoginRequest req, HttpServletRequest httpRequest) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS",
                        "Invalid email or password"));

        if (!user.isEnabled()) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "This account has been disabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        List<MembershipView> all = membershipCache.getMembershipsForUser(user.getId());
        if (all.isEmpty()) {
            throw new ForbiddenException("NO_ORG_MEMBERSHIP",
                    "Your account is not a member of any organization");
        }

        MembershipView active = pickActive(all, req.organizationId());
        user.setLastLoginAt(Instant.now());

        IssuedAccessToken access = jwtService.issueAccessToken(
                user.getId(), active.organizationId(), active.role());
        IssuedRefreshToken refresh = refreshTokens.issueNewSession(user, httpRequest);

        AuthResponse body = buildResponse(access, user, active, all);
        return new Result(body, refresh);
    }

    // ------------------------------------------------------------------- refresh

    @Override
    @Transactional
    public Result refresh(String presentedToken, UUID targetOrgId, HttpServletRequest httpRequest) {
        RotationResult rotation = refreshTokens.rotate(presentedToken, httpRequest);
        User user = rotation.user();

        List<MembershipView> all = membershipCache.getMembershipsForUser(user.getId());
        if (all.isEmpty()) {
            throw new ForbiddenException("NO_ORG_MEMBERSHIP",
                    "Your account is not a member of any organization");
        }
        MembershipView active = pickActive(all, targetOrgId);

        IssuedAccessToken access = jwtService.issueAccessToken(
                user.getId(), active.organizationId(), active.role());

        AuthResponse body = buildResponse(access, user, active, all);
        return new Result(body, rotation.newToken());
    }

    // ---------------------------------------------------------------- switch-org

    @Override
    @Transactional(readOnly = true)
    public Result switchOrg(UUID currentUserId, UUID targetOrgId, HttpServletRequest httpRequest) {
        List<MembershipView> all = membershipCache.getMembershipsForUser(currentUserId);
        MembershipView active = all.stream()
                .filter(m -> m.organizationId().equals(targetOrgId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("NOT_A_MEMBER",
                        "You are not a member of the requested organization"));

        User user = users.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_USER", "User not found"));

        IssuedAccessToken access = jwtService.issueAccessToken(
                user.getId(), active.organizationId(), active.role());

        AuthResponse body = buildResponse(access, user, active, all);
        return new Result(body, null);
    }

    // -------------------------------------------------------------------- logout

    @Override
    @Transactional
    public void logout(UUID sessionId) {
        if (sessionId != null) {
            refreshTokens.revokeSession(sessionId);
        }
    }

    // -------------------------------------------------------------- helper paths

    private static MembershipView pickActive(List<MembershipView> all, UUID targetOrgId) {
        if (targetOrgId == null) return all.get(0);
        return all.stream()
                .filter(m -> m.organizationId().equals(targetOrgId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("NOT_A_MEMBER",
                        "You are not a member of the requested organization"));
    }

    private static AuthResponse buildResponse(IssuedAccessToken access,
                                              User user,
                                              MembershipView active,
                                              List<MembershipView> all) {
        return new AuthResponse(
                access.token(),
                access.expiresAt(),
                toUserSummary(user),
                toOrgSummary(active),
                all.stream().map(AuthServiceImpl::toOrgSummary).toList()
        );
    }

    private static UserSummary toUserSummary(User u) {
        return new UserSummary(u.getId(), u.getEmail(), u.getFullName(), u.getAvatarUrl());
    }

    private static OrganizationSummary toOrgSummary(MembershipView m) {
        return new OrganizationSummary(m.organizationId(), m.organizationName(),
                m.organizationSlug(), m.role());
    }
}
