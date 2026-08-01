package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.cartisan.core.context.RequestContext;

/**
 * SSO 会话过滤器——identity 受保护接口的认人入口（全库唯一，ADR-0004 / issue #21）。
 *
 * <p>流程：读 SSO cookie → {@link SsoSessionRepository#findActive}（命中则滑动续期）→
 * 有效则用 {@link RequestContext#run} 绑定 {@code userId/displayName} 到 RequestContext 供下游读取；
 * 无有效会话且请求受保护路径 → 401。cartisan-security 移除后，RequestContext 只此一处绑定。</p>
 *
 * <p><b>注册</b>：经 {@code SsoWebConfig} 的 {@code FilterRegistrationBean} 注册（非 {@code @Component}），
 * 以免被 {@code @WebMvcTest} 切片扫描到（其依赖的 SsoSessionRepository 不在切片内）。</p>
 *
 * @since 0.1.0
 */
public class SsoSessionFilter extends OncePerRequestFilter {

    /** 尽早绑定 RequestContext（历史上紧随 cartisan SecurityFilter +5 之后，现为其唯一绑定来源）。 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 6;

    private static final String UNAUTHORIZED_BODY = "{\"code\":401,\"message\":\"未登录或会话已过期\"}";

    private final SsoSessionRepository sessionRepository;
    private final SsoCookieService cookieService;
    private final SsoProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SsoSessionFilter(SsoSessionRepository sessionRepository, SsoCookieService cookieService,
            SsoProperties properties) {
        this.sessionRepository = sessionRepository;
        this.cookieService = cookieService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> sessionId = cookieService.readSessionId(request);
        Optional<SsoSession> session = sessionId.flatMap(sessionRepository::findActive);

        if (session.isPresent()) {
            bindAndContinue(request, response, filterChain, session.get());
            return;
        }
        if (isProtected(request)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void bindAndContinue(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
            SsoSession session) {
        RequestContext current = RequestContext.CONTEXT.orElse(null);
        RequestContext context = (current != null ? current : bareContext())
            .withUser(session.userId(), session.displayName());
        RequestContext.run(context, () -> {
            try {
                filterChain.doFilter(request, response);
            } catch (IOException | ServletException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static RequestContext bareContext() {
        return new RequestContext(null, null, null, null, null, null, null, null);
    }

    private boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return properties.getProtectedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
