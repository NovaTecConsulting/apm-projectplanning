package rocks.nt.project.financials.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import javax.servlet.http.Cookie;

import com.vaadin.server.Page;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;

import io.opentracing.ActiveSpan;
import rocks.nt.project.financials.data.User;

/**
 * Simple authentication service.
 * 
 * @author awe
 *
 */
public class AuthService {

	/**
	 * Cookie name.
	 */
	private static final String COOKIE_NAME = "apm-projects-remember-me";

	/**
	 * Session user name parameter.
	 */
	public static final String SESSION_USER = "user";

	public static boolean isAuthenticated() {
		return VaadinSession.getCurrent().getAttribute(SESSION_USER) != null || loginRememberedUser();
	}

	public static boolean login(User user, String password, boolean rememberMe) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan("AuthService.login");
		s_this.setTag(JaegerUtil.T_USER_NAME, user.getLogin());

		try {
			// Logic
			if (UserService.getInstance().isAuthenticUser(user, password)) {
				return login(user, rememberMe);
			}

			return false;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}
	
	public static boolean loginGitHubUser(User user, Collection<String> organizations, boolean rememberMe) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan("AuthService.GitHub.login");
		s_this.setTag(JaegerUtil.T_USER_NAME, user.getLogin());

		try {
			// Logic
			if (UserService.getInstance().isAuthenticUser(user, organizations)) {
				return login(user, rememberMe);
			}

			return false;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	private static boolean login(User user, boolean rememberMe) {
		VaadinSession.getCurrent().setAttribute(SESSION_USER, user);
		
		if (rememberMe) {
			rememberUser(user);
		}
		return true;
	}

	public static void logOut() {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan("AuthService.logout");

		try {
			// Logic
			Optional<Cookie> cookie = getRememberMeCookie();
			if (cookie.isPresent()) {
				String id = cookie.get().getValue();
				UserService.getInstance().removeRememberedUser(id);
				deleteRememberMeCookie();
			}

			VaadinSession.getCurrent().close();
			Page.getCurrent().setLocation("");
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	private static Optional<Cookie> getRememberMeCookie() {
		Cookie[] cookies = VaadinService.getCurrentRequest().getCookies();
		return Arrays.stream(cookies).filter(c -> c.getName().equals(COOKIE_NAME)).findFirst();
	}

	private static boolean loginRememberedUser() {
		Optional<Cookie> rememberMeCookie = getRememberMeCookie();

		if (rememberMeCookie.isPresent()) {
			String id = rememberMeCookie.get().getValue();
			User user = UserService.getInstance().getRememberedUser(id);

			if (user != null) {
				VaadinSession.getCurrent().setAttribute(SESSION_USER, user);
				return true;
			}
		}

		return false;
	}

	private static void rememberUser(User user) {
		String id = UserService.getInstance().rememberUser(user);

		Cookie cookie = new Cookie(COOKIE_NAME, id);
		cookie.setPath("/");
		cookie.setMaxAge(60 * 60 * 24 * 30); // valid for 30 days
		VaadinService.getCurrentResponse().addCookie(cookie);
	}

	private static void deleteRememberMeCookie() {
		Cookie cookie = new Cookie(COOKIE_NAME, "");
		cookie.setPath("/");
		cookie.setMaxAge(0);
		VaadinService.getCurrentResponse().addCookie(cookie);
	}
}
