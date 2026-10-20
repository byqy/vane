package xyz.byqy.vane.iam.api.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.problem.ProblemDetailWriter;
import xyz.byqy.vane.iam.application.AuthService;
import xyz.byqy.vane.iam.application.AuthorityService;
import xyz.byqy.vane.iam.domain.IamUser;

/** 解析 Authorization: Bearer，校验 access 令牌有效性并建立安全上下文。 */
@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AuthService authService;
    private final AuthorityService authorityService;
    private final ProblemDetailWriter problemWriter;

    public BearerAuthenticationFilter(AuthService authService, AuthorityService authorityService,
                                       ProblemDetailWriter problemWriter) {
        this.authService = authService;
        this.authorityService = authorityService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(PREFIX.length()).trim();
        var user = authService.authenticate(token);
        if (user.isEmpty()) {
            problemWriter.write(request, response, CommonErrors.AUTH_TOKEN_INVALID);
            return;
        }
        IamUser principal = user.get();
        List<SimpleGrantedAuthority> authorities = authorityService.codesOf(principal.getId()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(principal.getId(), principal.getUsername(), principal.getDeptId()),
                null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}

